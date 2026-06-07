package io.github.ajsb85.usbserial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import android.os.Build
import io.github.ajsb85.usbserial.UsbSerialPort.ControlLine
import io.github.ajsb85.usbserial.UsbSerialPort.FlowControl
import io.github.ajsb85.usbserial.util.MonotonicClock
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Base class shared by the concrete drivers. Implements the open/close lifecycle and the
 * timeout-aware bulk read/write, leaving chip-specific control transfers to subclasses
 * ([openInt], [closeInt], [setParameters], and the control-line overrides).
 *
 * Robustness choices for production use: a [MonotonicClock] governs all timeouts, failed
 * transfers are disambiguated from disconnects via [testConnection] (a cheap GET_STATUS request),
 * and partial writes are retried within the caller's deadline.
 */
abstract class CommonUsbSerialPort(
    final override val driver: UsbSerialDriver,
    final override val device: UsbDevice,
    final override val portNumber: Int,
) : UsbSerialPort {

    private companion object {
        const val MAX_READ_SIZE = 16 * 1024 // bulkTransfer limit prior to Android 9
    }

    protected var connection: UsbDeviceConnection? = null
        private set

    final override var readEndpoint: UsbEndpoint? = null
        protected set
    final override var writeEndpoint: UsbEndpoint? = null
        protected set

    protected var flowControlField: FlowControl = FlowControl.NONE
    final override val flowControl: FlowControl get() = flowControlField

    private var readRequest: UsbRequest? = null
    private var writeBuffer: ByteArray? = null
    private val writeBufferLock = Any()

    final override val isOpen: Boolean get() = readRequest != null
    final override val serial: String? get() = connection?.serial

    override fun toString(): String = "<${javaClass.simpleName} device=${device.deviceName} id=${device.deviceId} port=$portNumber>"

    final override fun open(connection: UsbDeviceConnection) {
        if (this.connection != null) throw IOException("Already open")
        this.connection = connection
        var ok = false
        try {
            openInt()
            if (readEndpoint == null || writeEndpoint == null) {
                throw IOException("Could not get read & write endpoints")
            }
            readRequest = UsbRequest().apply { initialize(connection, readEndpoint) }
            ok = true
        } finally {
            if (!ok) runCatching { close() }
        }
    }

    /** Claim interfaces, discover endpoints, and apply initial chip configuration. */
    protected abstract fun openInt()

    final override fun close() {
        val conn = connection ?: throw IOException("Already closed")
        val request = readRequest
        readRequest = null
        runCatching { request?.cancel() }
        runCatching { closeInt() }
        runCatching { conn.close() }
        connection = null
    }

    /** Release interfaces and undo chip configuration. Must not throw. */
    protected abstract fun closeInt()

    /** Cheap liveness probe: a standard GET_STATUS control request. */
    protected fun testConnection(full: Boolean, msg: String = "USB get_status request failed") {
        if (readRequest == null) throw IOException("Connection closed")
        if (!full) return
        val buf = ByteArray(2)
        val len = connection!!.controlTransfer(0x80, 0, 0, 0, buf, buf.size, 200)
        if (len < 0) throw IOException(msg)
    }

    final override fun read(dest: ByteArray, timeoutMillis: Int): Int {
        require(dest.isNotEmpty()) { "Read buffer too small" }
        return read(dest, dest.size, timeoutMillis)
    }

    // UsbRequest.queue(ByteBuffer, Int) is deprecated since API 26 but is the only form available
    // on our minSdk (21); the 1-arg replacement requires API 26+.
    @Suppress("DEPRECATION")
    final override fun read(dest: ByteArray, length: Int, timeoutMillis: Int): Int {
        testConnection(false)
        require(length > 0) { "Read length too small" }
        val conn = connection ?: throw IOException("Connection closed")
        val want = minOf(length, dest.size)
        val nread: Int
        if (timeoutMillis != 0) {
            val endTime = MonotonicClock.millis() + timeoutMillis
            val readMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) want else minOf(want, MAX_READ_SIZE)
            nread = conn.bulkTransfer(readEndpoint, dest, readMax, timeoutMillis)
            if (nread == -1) testConnection(MonotonicClock.millis() < endTime)
        } else {
            val buf = ByteBuffer.wrap(dest, 0, want)
            val request = readRequest ?: throw IOException("Connection closed")
            if (!request.queue(buf, want)) throw IOException("Queueing USB request failed")
            conn.requestWait() ?: throw IOException("Waiting for USB request failed")
            nread = buf.position()
            if (nread == 0) testConnection(true)
        }
        return maxOf(nread, 0)
    }

    final override fun write(src: ByteArray, timeoutMillis: Int) = write(src, src.size, timeoutMillis)

    final override fun write(src: ByteArray, length: Int, timeoutMillis: Int) {
        var offset = 0
        val startTime = MonotonicClock.millis()
        val total = minOf(length, src.size)
        testConnection(false)
        val conn = connection ?: throw IOException("Connection closed")
        val ep = writeEndpoint ?: throw IOException("Connection closed")

        while (offset < total) {
            var requestLength: Int
            var actualLength: Int
            var requestTimeout: Int
            synchronized(writeBufferLock) {
                val wb = writeBuffer ?: ByteArray(ep.maxPacketSize).also { writeBuffer = it }
                requestLength = minOf(total - offset, wb.size)
                val writeBuf: ByteArray = if (offset == 0) {
                    src
                } else {
                    System.arraycopy(src, offset, wb, 0, requestLength)
                    wb
                }
                requestTimeout = if (timeoutMillis == 0 || offset == 0) {
                    timeoutMillis
                } else {
                    (startTime + timeoutMillis - MonotonicClock.millis()).toInt().let { if (it == 0) -1 else it }
                }
                actualLength = if (requestTimeout < 0) -2 else conn.bulkTransfer(ep, writeBuf, requestLength, requestTimeout)
            }
            if (actualLength <= 0) {
                val elapsed = MonotonicClock.millis() - startTime
                val msg = "Error writing $requestLength bytes at offset $offset of ${src.size} after ${elapsed}ms, rc=$actualLength"
                if (timeoutMillis != 0) {
                    testConnection(elapsed < timeoutMillis, msg)
                    throw SerialTimeoutException(msg, offset)
                }
                throw IOException(msg)
            }
            offset += actualLength
        }
    }

    // --- optional capabilities; drivers override what they support ---

    override fun getCD(): Boolean = throw UnsupportedOperationException()
    override fun getCTS(): Boolean = throw UnsupportedOperationException()
    override fun getDSR(): Boolean = throw UnsupportedOperationException()
    override fun getDTR(): Boolean = throw UnsupportedOperationException()
    override fun setDTR(value: Boolean): Unit = throw UnsupportedOperationException()
    override fun getRI(): Boolean = throw UnsupportedOperationException()
    override fun getRTS(): Boolean = throw UnsupportedOperationException()
    override fun setRTS(value: Boolean): Unit = throw UnsupportedOperationException()
    override fun getControlLines(): Set<ControlLine> = throw UnsupportedOperationException()
    override fun getSupportedControlLines(): Set<ControlLine> = emptySet()
    override val supportedFlowControl: Set<FlowControl> get() = setOf(FlowControl.NONE)

    override fun setFlowControl(value: FlowControl) {
        if (value != FlowControl.NONE) throw UnsupportedOperationException()
    }

    override fun purgeHwBuffers(purgeWriteBuffers: Boolean, purgeReadBuffers: Boolean): Unit = throw UnsupportedOperationException()

    override fun setBreak(value: Boolean): Unit = throw UnsupportedOperationException()
}
