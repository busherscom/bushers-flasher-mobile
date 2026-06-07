package io.github.ajsb85.usbserial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.Closeable
import java.io.IOException

/**
 * A single serial port exposed by a USB-serial bridge.
 *
 * This is the Kotlin equivalent of usb-serial-for-android's `UsbSerialPort`, redesigned for
 * idiomatic Kotlin and enterprise robustness (explicit nullability, `@Throws` for Java interop,
 * no reflection). All blocking calls accept a `timeoutMillis` where `0` means "block indefinitely".
 */
interface UsbSerialPort : Closeable {

    /** The driver that produced this port. */
    val driver: UsbSerialDriver

    /** The backing USB device. */
    val device: UsbDevice

    /** Index of this port within its driver (0-based). */
    val portNumber: Int

    /** The bulk IN endpoint, available after [open]. */
    val readEndpoint: UsbEndpoint?

    /** The bulk OUT endpoint, available after [open]. */
    val writeEndpoint: UsbEndpoint?

    /** Underlying connection serial number, or null. May throw [SecurityException] on API 29+. */
    val serial: String?

    /** True between a successful [open] and [close]. */
    val isOpen: Boolean

    /** Modem/handshake control lines. */
    enum class ControlLine { RTS, CTS, DTR, DSR, CD, RI }

    /** Hardware/software flow-control modes. */
    enum class FlowControl { NONE, RTS_CTS, DTR_DSR, XON_XOFF, XON_XOFF_INLINE }

    /** Open and initialize the port on an already-opened [connection]. */
    @Throws(IOException::class)
    fun open(connection: UsbDeviceConnection)

    /** Close the port and its [UsbDeviceConnection]. Safe to call once after [open]. */
    @Throws(IOException::class)
    override fun close()

    /** Read up to `dest.size` bytes; returns the number read (0 on timeout). */
    @Throws(IOException::class)
    fun read(dest: ByteArray, timeoutMillis: Int): Int

    /** Read up to [length] bytes into [dest]; returns the number read (0 on timeout). */
    @Throws(IOException::class)
    fun read(dest: ByteArray, length: Int, timeoutMillis: Int): Int

    /** Write all of [src]; throws [SerialTimeoutException] if not sent within [timeoutMillis]. */
    @Throws(IOException::class)
    fun write(src: ByteArray, timeoutMillis: Int)

    /** Write the first [length] bytes of [src]. */
    @Throws(IOException::class)
    fun write(src: ByteArray, length: Int, timeoutMillis: Int)

    /** Configure line parameters. See the `DATABITS_*`, `STOPBITS_*`, `PARITY_*` constants. */
    @Throws(IOException::class)
    fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int)

    @Throws(IOException::class)
    fun getCD(): Boolean

    @Throws(IOException::class)
    fun getCTS(): Boolean

    @Throws(IOException::class)
    fun getDSR(): Boolean

    @Throws(IOException::class)
    fun getDTR(): Boolean

    @Throws(IOException::class)
    fun setDTR(value: Boolean)

    @Throws(IOException::class)
    fun getRI(): Boolean

    @Throws(IOException::class)
    fun getRTS(): Boolean

    @Throws(IOException::class)
    fun setRTS(value: Boolean)

    /** Read all control lines at once (fewer USB round-trips than individual getters). */
    @Throws(IOException::class)
    fun getControlLines(): Set<ControlLine>

    /** Control lines this device can report. */
    @Throws(IOException::class)
    fun getSupportedControlLines(): Set<ControlLine>

    /** Current flow-control mode. */
    val flowControl: FlowControl

    /** Flow-control modes this device supports. */
    val supportedFlowControl: Set<FlowControl>

    @Throws(IOException::class)
    fun setFlowControl(value: FlowControl)

    /** Discard non-transmitted output and/or non-read input data, if supported. */
    @Throws(IOException::class)
    fun purgeHwBuffers(purgeWriteBuffers: Boolean, purgeReadBuffers: Boolean)

    /** Assert/clear a BREAK condition. */
    @Throws(IOException::class)
    fun setBreak(value: Boolean)

    companion object {
        const val DATABITS_5 = 5
        const val DATABITS_6 = 6
        const val DATABITS_7 = 7
        const val DATABITS_8 = 8

        const val PARITY_NONE = 0
        const val PARITY_ODD = 1
        const val PARITY_EVEN = 2
        const val PARITY_MARK = 3
        const val PARITY_SPACE = 4

        const val STOPBITS_1 = 1
        const val STOPBITS_1_5 = 3
        const val STOPBITS_2 = 2

        const val CHAR_XON = 17
        const val CHAR_XOFF = 19
    }
}
