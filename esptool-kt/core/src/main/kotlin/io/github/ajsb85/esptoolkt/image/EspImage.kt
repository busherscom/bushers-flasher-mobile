package io.github.ajsb85.esptoolkt.image

import io.github.ajsb85.esptoolkt.protocol.Bytes
import io.github.ajsb85.esptoolkt.protocol.EspProtocolException

/** One loadable segment of a parsed ESP firmware image. */
class EspImageSegment(val address: Long, val data: ByteArray)

/**
 * A parsed ESP application/bootloader image (magic `0xE9`). Used by `image_info` to list
 * structure and by `load_ram` to stream segments into RAM and jump to [entry].
 *
 * ESP32-family images carry a 16-byte extended header after the 8-byte main header
 * (containing chip_id and revision constraints); ESP8266 images do not. Pass
 * [hasExtendedHeader] accordingly (true for all ESP32 variants).
 */
class EspImage(
    val entry: Long,
    val flashMode: Int,
    val flashSizeFreq: Int,
    val chipId: Int?,
    val hashAppended: Boolean,
    val segments: List<EspImageSegment>,
) {
    fun describe(): String = buildString {
        appendLine("Image magic:    0xE9")
        appendLine("Entry point:    0x%08x".format(entry))
        chipId?.let { appendLine("Chip ID:        $it") }
        appendLine("Flash mode:     ${FlashMode.entries.firstOrNull { it.code == flashMode }?.name ?: "0x%x".format(flashMode)}")
        appendLine("Segments:       ${segments.size}")
        segments.forEachIndexed { i, s ->
            appendLine("  [%d] 0x%08x  %6d bytes".format(i, s.address, s.data.size))
        }
        append("SHA256 appended: $hashAppended")
    }

    companion object {
        const val MAGIC = 0xE9

        fun parse(data: ByteArray, hasExtendedHeader: Boolean): EspImage {
            if (data.size < 8 || (data[0].toInt() and 0xFF) != MAGIC) {
                throw EspProtocolException("Not an ESP image (first byte != 0xE9)")
            }
            val segCount = data[1].toInt() and 0xFF
            val flashMode = data[2].toInt() and 0xFF
            val flashSizeFreq = data[3].toInt() and 0xFF
            val entry = Bytes.readU32(data, 4)

            var offset = 8
            var chipId: Int? = null
            var hashAppended = false
            if (hasExtendedHeader) {
                require(data.size >= offset + 16) { "Truncated extended header" }
                chipId = Bytes.readU16(data, offset + 4) // after wp_pin(1)+spi_pin_drv(3)
                hashAppended = (data[offset + 15].toInt() and 0xFF) != 0
                offset += 16
            }

            val segments = ArrayList<EspImageSegment>(segCount)
            repeat(segCount) {
                require(data.size >= offset + 8) { "Truncated segment header" }
                val addr = Bytes.readU32(data, offset)
                val len = Bytes.readU32(data, offset + 4).toInt()
                offset += 8
                require(len >= 0 && data.size >= offset + len) { "Truncated segment data" }
                segments.add(EspImageSegment(addr, data.copyOfRange(offset, offset + len)))
                offset += len
            }
            return EspImage(entry, flashMode, flashSizeFreq, chipId, hashAppended, segments)
        }
    }
}
