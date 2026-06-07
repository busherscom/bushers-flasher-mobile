package io.github.ajsb85.esptoolkt.flasher

import io.github.ajsb85.esptoolkt.EspLoader
import io.github.ajsb85.esptoolkt.EspLogger
import io.github.ajsb85.esptoolkt.NoopLogger
import io.github.ajsb85.esptoolkt.image.FirmwareImage
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.protocol.EspMd5MismatchException
import io.github.ajsb85.esptoolkt.targets.Chips
import java.security.MessageDigest
import java.util.zip.Deflater

/** One image to be written at a flash [offset]. */
class FlashSegment(val offset: Long, val data: ByteArray, val name: String = "0x%x".format(offset))

/**
 * High-level flashing orchestration on top of [EspLoader] — the equivalent of esptool's
 * `write_flash`/`verify_flash`. Handles bootloader-header patching, optional zlib compression,
 * block streaming, and per-image MD5 verification.
 */
class Flasher(
    private val loader: EspLoader,
    private val logger: EspLogger = NoopLogger,
) {
    /**
     * Write each segment, patching flash params into the image at the chip's bootloader
     * offset (per [settings]). With [compress] (default) data is zlib-deflated and sent via
     * FLASH_DEFL_*; with [verify] (default) each image's flash MD5 is checked afterwards.
     */
    fun writeFlash(
        segments: List<FlashSegment>,
        settings: FlashSettings = FlashSettings(),
        compress: Boolean = true,
        verify: Boolean = true,
    ) {
        loader.initFlashParams(settings.size?.bytes)
        val bootOffset = bootloaderOffset()
        for (segment in segments) {
            val data = if (segment.offset == bootOffset) {
                FirmwareImage.patchFlashParams(segment.data, settings)
            } else {
                segment.data
            }
            writeSegment(segment.name, segment.offset, data, compress)
            if (verify) verifySegment(segment.offset, data)
        }
        // Tell the stub we're done (no-op on the ROM loader, which reboots via reset).
        if (compress) loader.flashDeflFinish() else loader.flashFinish()
    }

    private fun writeSegment(name: String, offset: Long, data: ByteArray, compress: Boolean) {
        val blockSize = loader.flashWriteSize()
        if (compress) {
            val compressed = deflate(data)
            val blocks = ceilDiv(compressed.size, blockSize)
            val eraseBlocks = ceilDiv(data.size, blockSize)
            val writeSize = if (loader.stubRunning) data.size.toLong() else (eraseBlocks.toLong() * blockSize)
            logger.info("Writing $name (${data.size} bytes, ${compressed.size} compressed) at 0x%x".format(offset))
            loader.flashDeflBegin(writeSize, blocks, blockSize, offset, data.size.toLong())
            var pos = 0
            while (pos < compressed.size) {
                val end = minOf(pos + blockSize, compressed.size)
                loader.flashDeflBlock(compressed.copyOfRange(pos, end))
                pos = end
                logger.progress(name, pos.toLong(), compressed.size.toLong())
            }
        } else {
            val blocks = ceilDiv(data.size, blockSize)
            logger.info("Writing $name (${data.size} bytes) at 0x%x".format(offset))
            loader.flashBegin(offset, data.size.toLong(), blockSize, blocks)
            var pos = 0
            while (pos < data.size) {
                val end = minOf(pos + blockSize, data.size)
                var block = data.copyOfRange(pos, end)
                if (block.size < blockSize) {
                    block = block.copyOf(blockSize).also { padded ->
                        for (i in block.size until blockSize) padded[i] = 0xFF.toByte()
                    }
                }
                loader.flashBlock(block)
                pos = end
                logger.progress(name, minOf(pos, data.size).toLong(), data.size.toLong())
            }
        }
    }

    private fun verifySegment(offset: Long, data: ByteArray) {
        val expected = md5Hex(data)
        val actual = loader.flashMd5(offset, data.size.toLong())
        if (!expected.equals(actual, ignoreCase = true)) {
            throw EspMd5MismatchException(
                "MD5 mismatch at 0x%x: expected %s, flash reports %s".format(offset, expected, actual),
            )
        }
        logger.info("Hash of data verified at 0x%x".format(offset))
    }

    /** Verify a flash region matches [data] without writing (the `verify_flash` command). */
    fun verifyFlash(offset: Long, data: ByteArray): Boolean {
        loader.initFlashParams()
        return md5Hex(data).equals(loader.flashMd5(offset, data.size.toLong()), ignoreCase = true)
    }

    /** Bootloader offset for the detected chip; the image written here gets its header patched. */
    private fun bootloaderOffset(): Long = when (loader.chip) {
        Chips.ESP32, Chips.ESP32S2, Chips.ESP32S3 -> 0x1000
        Chips.ESP32C5, Chips.ESP32C61, Chips.ESP32P4 -> 0x2000 // newer chips with a larger ROM
        else -> 0x0 // ESP8266 and earlier RISC-V targets (C2/C3/C6/H2) place the bootloader at 0
    }

    companion object {
        private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

        fun deflate(data: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(data)
            deflater.finish()
            val out = java.io.ByteArrayOutputStream(maxOf(64, data.size / 2))
            val buf = ByteArray(16384)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                out.write(buf, 0, n)
            }
            deflater.end()
            return out.toByteArray()
        }

        fun md5Hex(data: ByteArray): String = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
    }
}
