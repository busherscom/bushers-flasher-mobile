package io.github.ajsb85.esptoolkt.jvm

import com.fazecast.jSerialComm.SerialPort
import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.transport.SerialTransport

/**
 * Desktop/Linux [SerialTransport] backed by jSerialComm. Configures the port as 8N1 with no
 * flow control and per-read timeouts, matching the way esptool talks to the bootloader.
 */
class JSerialCommTransport private constructor(
    private val port: SerialPort,
) : SerialTransport {

    override var baudRate: Int
        get() = port.baudRate
        set(value) {
            port.baudRate = value
        }

    override fun read(dst: ByteArray, length: Int, timeoutMs: Int): Int {
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, timeoutMs.coerceAtLeast(1), 0)
        val n = port.readBytes(dst, length)
        return if (n < 0) 0 else n
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        val buf = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        var written = 0
        while (written < length) {
            val n = port.writeBytes(buf, length - written, written)
            if (n < 0) throw EspException("Serial write failed on ${port.systemPortName}")
            written += n
        }
    }

    override fun setDtr(value: Boolean) {
        if (value) port.setDTR() else port.clearDTR()
    }

    override fun setRts(value: Boolean) {
        if (value) port.setRTS() else port.clearRTS()
    }

    override fun flushInput() {
        // Drain any bytes already buffered by the OS/driver.
        val tmp = ByteArray(1024)
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
        while (port.bytesAvailable() > 0) {
            if (port.readBytes(tmp, tmp.size) <= 0) break
        }
    }

    override fun close() {
        if (port.isOpen) port.closePort()
    }

    companion object {
        /**
         * Open [portName] (e.g. "/dev/ttyUSB0" or "COM3") at [baud] bps, 8N1, no flow control.
         */
        fun open(portName: String, baud: Int = 115200): JSerialCommTransport {
            val port = SerialPort.getCommPort(portName)
            port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0)
            if (!port.openPort()) {
                throw EspException("Could not open serial port '$portName' (in use or permission denied)")
            }
            return JSerialCommTransport(port)
        }
    }
}
