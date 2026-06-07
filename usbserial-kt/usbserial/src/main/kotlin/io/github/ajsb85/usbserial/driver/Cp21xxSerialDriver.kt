package io.github.ajsb85.usbserial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import io.github.ajsb85.usbserial.CommonUsbSerialPort
import io.github.ajsb85.usbserial.UsbId
import io.github.ajsb85.usbserial.UsbSerialDriver
import io.github.ajsb85.usbserial.UsbSerialDriverFactory
import io.github.ajsb85.usbserial.UsbSerialPort
import io.github.ajsb85.usbserial.UsbSerialPort.ControlLine
import io.github.ajsb85.usbserial.UsbSerialPort.FlowControl
import java.io.IOException

/** Driver for Silicon Labs CP210x bridges (CP2102/3/4/5/8/9) — the most common ESP dev-board UART. */
class Cp21xxSerialDriver(override val device: UsbDevice) : UsbSerialDriver {

    override val ports: List<UsbSerialPort> =
        (0 until device.interfaceCount).map { Cp21xxSerialPort(this, device, it) }

    private class Cp21xxSerialPort(
        driver: UsbSerialDriver,
        device: UsbDevice,
        portNumber: Int,
    ) : CommonUsbSerialPort(driver, device, portNumber) {

        private var dtr = false
        private var rts = false
        private var restrictedPort = false

        private fun setConfigSingle(request: Int, value: Int) {
            val result = connection!!.controlTransfer(REQTYPE_HOST_TO_DEVICE, request, value, portNumber, null, 0, WRITE_TIMEOUT)
            if (result != 0) {
                System.err.println("Cp21xx Control transfer warning: $request / $value -> $result")
            }
        }

        private fun status(): Int {
            val buf = ByteArray(1)
            val result = connection!!.controlTransfer(REQTYPE_DEVICE_TO_HOST, GET_MDMSTS, 0, portNumber, buf, buf.size, WRITE_TIMEOUT)
            if (result != buf.size) throw IOException("Control transfer failed: $GET_MDMSTS -> $result")
            return buf[0].toInt() and 0xFF
        }

        override fun openInt() {
            restrictedPort = device.interfaceCount == 2 && portNumber == 1
            if (portNumber >= device.interfaceCount) throw IOException("Unknown port number")
            val dataIface = device.getInterface(portNumber)
            if (!connection!!.claimInterface(dataIface, true)) throw IOException("Could not claim interface $portNumber")
            for (i in 0 until dataIface.endpointCount) {
                val ep = dataIface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) readEndpoint = ep else writeEndpoint = ep
                }
            }
            setConfigSingle(IFC_ENABLE, UART_ENABLE)
            setConfigSingle(SET_MHS, (if (dtr) DTR_ENABLE else DTR_DISABLE) or (if (rts) RTS_ENABLE else RTS_DISABLE))
        }

        override fun closeInt() {
            runCatching { setConfigSingle(IFC_ENABLE, UART_DISABLE) }
            runCatching { connection!!.releaseInterface(device.getInterface(portNumber)) }
        }

        private fun setBaudRate(baudRate: Int) {
            val data = byteArrayOf(
                (baudRate and 0xff).toByte(),
                ((baudRate shr 8) and 0xff).toByte(),
                ((baudRate shr 16) and 0xff).toByte(),
                ((baudRate shr 24) and 0xff).toByte(),
            )
            val ret = connection!!.controlTransfer(REQTYPE_HOST_TO_DEVICE, SET_BAUDRATE, 0, portNumber, data, 4, WRITE_TIMEOUT)
            if (ret < 0) {
                System.err.println("Cp21xx Error setting baud rate warning: $ret")
            }
        }

        override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
            require(baudRate > 0) { "Invalid baud rate: $baudRate" }
            setBaudRate(baudRate)
            var cfg = when (dataBits) {
                UsbSerialPort.DATABITS_5, UsbSerialPort.DATABITS_6, UsbSerialPort.DATABITS_7 -> {
                    if (restrictedPort) throw UnsupportedOperationException("Unsupported data bits: $dataBits")
                    dataBits shl 8
                }
                UsbSerialPort.DATABITS_8 -> 0x0800
                else -> throw IllegalArgumentException("Invalid data bits: $dataBits")
            }
            cfg = cfg or when (parity) {
                UsbSerialPort.PARITY_NONE -> 0
                UsbSerialPort.PARITY_ODD -> 0x0010
                UsbSerialPort.PARITY_EVEN -> 0x0020
                UsbSerialPort.PARITY_MARK -> {
                    if (restrictedPort) throw UnsupportedOperationException("Unsupported parity: mark")
                    0x0030
                }
                UsbSerialPort.PARITY_SPACE -> {
                    if (restrictedPort) throw UnsupportedOperationException("Unsupported parity: space")
                    0x0040
                }
                else -> throw IllegalArgumentException("Invalid parity: $parity")
            }
            cfg = cfg or when (stopBits) {
                UsbSerialPort.STOPBITS_1 -> 0
                UsbSerialPort.STOPBITS_1_5 -> throw UnsupportedOperationException("Unsupported stop bits: 1.5")
                UsbSerialPort.STOPBITS_2 -> {
                    if (restrictedPort) throw UnsupportedOperationException("Unsupported stop bits: 2")
                    2
                }
                else -> throw IllegalArgumentException("Invalid stop bits: $stopBits")
            }
            setConfigSingle(SET_LINE_CTL, cfg)
        }

        override fun getCD() = status() and STATUS_CD != 0
        override fun getCTS() = status() and STATUS_CTS != 0
        override fun getDSR() = status() and STATUS_DSR != 0
        override fun getRI() = status() and STATUS_RI != 0
        override fun getDTR() = dtr
        override fun getRTS() = rts

        override fun setDTR(value: Boolean) {
            dtr = value
            setConfigSingle(SET_MHS, if (dtr) DTR_ENABLE else DTR_DISABLE)
        }

        override fun setRTS(value: Boolean) {
            rts = value
            setConfigSingle(SET_MHS, if (rts) RTS_ENABLE else RTS_DISABLE)
        }

        override fun getControlLines(): Set<ControlLine> {
            val s = status()
            return buildSet {
                if (s and STATUS_RTS != 0) add(ControlLine.RTS)
                if (s and STATUS_CTS != 0) add(ControlLine.CTS)
                if (s and STATUS_DTR != 0) add(ControlLine.DTR)
                if (s and STATUS_DSR != 0) add(ControlLine.DSR)
                if (s and STATUS_CD != 0) add(ControlLine.CD)
                if (s and STATUS_RI != 0) add(ControlLine.RI)
            }
        }

        override fun getSupportedControlLines(): Set<ControlLine> = ControlLine.entries.toSet()

        override fun purgeHwBuffers(purgeWriteBuffers: Boolean, purgeReadBuffers: Boolean) {
            val value = (if (purgeReadBuffers) FLUSH_READ else 0) or (if (purgeWriteBuffers) FLUSH_WRITE else 0)
            if (value != 0) setConfigSingle(FLUSH, value)
        }

        override fun setBreak(value: Boolean) = setConfigSingle(SET_BREAK, if (value) 1 else 0)

        private companion object {
            const val WRITE_TIMEOUT = 5000
            const val REQTYPE_HOST_TO_DEVICE = 0x41
            const val REQTYPE_DEVICE_TO_HOST = 0xc1
            const val IFC_ENABLE = 0x00
            const val SET_LINE_CTL = 0x03
            const val SET_BREAK = 0x05
            const val SET_MHS = 0x07
            const val GET_MDMSTS = 0x08
            const val FLUSH = 0x12
            const val SET_BAUDRATE = 0x1E
            const val FLUSH_READ = 0x0a
            const val FLUSH_WRITE = 0x05
            const val UART_ENABLE = 0x0001
            const val UART_DISABLE = 0x0000
            const val DTR_ENABLE = 0x101
            const val DTR_DISABLE = 0x100
            const val RTS_ENABLE = 0x202
            const val RTS_DISABLE = 0x200
            const val STATUS_CTS = 0x10
            const val STATUS_DSR = 0x20
            const val STATUS_RI = 0x40
            const val STATUS_CD = 0x80
            const val STATUS_DTR = 0x01
            const val STATUS_RTS = 0x02
        }

        // setFlowControl: only NONE supported in this port (RTS/CTS available via control lines).
        override val supportedFlowControl: Set<FlowControl> get() = setOf(FlowControl.NONE)
    }

    /** Factory for the default prober. */
    object Factory : UsbSerialDriverFactory {
        override val supportedDevices: Map<Int, IntArray> = mapOf(
            UsbId.VENDOR_SILABS to intArrayOf(UsbId.SILABS_CP2102, UsbId.SILABS_CP2102C, UsbId.SILABS_CP2105, UsbId.SILABS_CP2108),
        )

        override fun create(device: UsbDevice): UsbSerialDriver = Cp21xxSerialDriver(device)
    }
}
