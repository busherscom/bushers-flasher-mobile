package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.targets.Chip
import io.github.ajsb85.esptoolkt.transport.ClassicReset
import io.github.ajsb85.esptoolkt.transport.HardReset
import io.github.ajsb85.esptoolkt.transport.ResetStrategy
import io.github.ajsb85.esptoolkt.transport.SerialTransport

/**
 * High-level, batteries-included facade over [EspLoader] for embedding in apps — especially the
 * upcoming Android `.aar`. Handles the connect → (stub) → (baud) lifecycle and exposes the common
 * operations as one-liners, with progress delivered through [EspLogger].
 *
 * Typical Android usage:
 * ```
 * EspTool(UsbSerialTransport(device, connection), logger = myLogger).use { tool ->
 *     val chip = tool.connect()
 *     tool.writeFlash(listOf(FlashSegment(0x10000, firmwareBytes)))
 * }   // close() resets the chip into the app and releases the port
 * ```
 */
class EspTool(
    private val transport: SerialTransport,
    private val before: ResetStrategy = ClassicReset(),
    private val after: ResetStrategy = HardReset(),
    private val useStub: Boolean = true,
    private val flashBaud: Int = 460_800,
    val logger: EspLogger = NoopLogger,
) : AutoCloseable {

    val loader: EspLoader = EspLoader(transport, logger)
    private val flasher = Flasher(loader, logger)

    var connected: Boolean = false
        private set

    /** Reset into the bootloader, sync, detect the chip, load the stub, and raise the baud. */
    fun connect(): Chip {
        loader.connect(before)
        if (useStub) loader.runStub()
        if (flashBaud != transport.baudRate) loader.changeBaud(flashBaud)
        connected = true
        return loader.chip ?: error("Chip detection failed")
    }

    fun writeFlash(
        segments: List<FlashSegment>,
        settings: FlashSettings = FlashSettings(),
        compress: Boolean = true,
        verify: Boolean = true,
    ) = flasher.writeFlash(segments, settings, compress, verify)

    fun readFlash(address: Long, length: Int): ByteArray = loader.readFlash(address, length)
    fun eraseFlash() = loader.eraseFlash()
    fun eraseRegion(offset: Long, size: Long) = loader.eraseRegion(offset, size)
    fun verifyFlash(offset: Long, data: ByteArray): Boolean = flasher.verifyFlash(offset, data)

    fun readMac(): ByteArray = loader.readMac()
    fun securityInfo(): SecurityInfo? = loader.securityInfo()
    fun flashId(): Long = loader.readFlashId()
    fun detectFlashSize(): Long? = loader.detectFlashSize()

    /** Reboot the chip into the application without closing the port. */
    fun reset() = loader.hardReset(after)

    override fun close() {
        runCatching { if (connected) loader.hardReset(after) }
        transport.close()
    }
}
