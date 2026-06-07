package io.github.ajsb85.esptoolkt.transport

/**
 * Platform-agnostic serial port abstraction.
 *
 * This is the single seam between the protocol [io.github.ajsb85.esptoolkt.EspLoader]
 * engine and the host platform. On desktop/Linux it is backed by jSerialComm
 * (`jvm-serial` module); on Android it will be backed by `usb-serial-for-android`
 * (phase 2). The core never references either directly.
 *
 * Implementations must be usable for raw, unbuffered byte exchange — the protocol
 * layer ([io.github.ajsb85.esptoolkt.protocol.SerialReader]) adds its own buffering
 * and SLIP framing on top.
 */
interface SerialTransport : AutoCloseable {

    /** Current line speed in bits per second. Setting it must reconfigure the port. */
    var baudRate: Int

    /**
     * Read up to [length] bytes into [dst] starting at index 0, blocking at most
     * [timeoutMs] milliseconds. Returns the number of bytes actually read, which
     * may be `0` if the timeout elapsed before any byte arrived.
     */
    fun read(dst: ByteArray, length: Int, timeoutMs: Int): Int

    /** Write [length] bytes from [data] starting at [offset], blocking until sent. */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size)

    /** Assert/deassert the DTR control line (used by reset strategies). */
    fun setDtr(value: Boolean)

    /** Assert/deassert the RTS control line (used by reset strategies). */
    fun setRts(value: Boolean)

    /** Discard any buffered, unread input. */
    fun flushInput()

    /** Block until all queued output bytes have been transmitted. */
    fun flushOutput() {}

    override fun close()
}
