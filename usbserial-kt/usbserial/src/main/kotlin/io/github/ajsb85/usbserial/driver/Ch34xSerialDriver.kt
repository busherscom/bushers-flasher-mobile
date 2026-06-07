package io.github.ajsb85.usbserial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import io.github.ajsb85.usbserial.CommonUsbSerialPort
import io.github.ajsb85.usbserial.UsbId
import io.github.ajsb85.usbserial.UsbSerialDriver
import io.github.ajsb85.usbserial.UsbSerialDriverFactory
import io.github.ajsb85.usbserial.UsbSerialPort
import io.github.ajsb85.usbserial.UsbSerialPort.ControlLine
import java.io.IOException

/** Driver for WCH CH340/CH341 bridges — very common on low-cost ESP boards. */
class Ch34xSerialDriver(override val device: UsbDevice) : UsbSerialDriver {

    override val ports: List<UsbSerialPort> = listOf(Ch340SerialPort(this, device, 0))

    private class Ch340SerialPort(
        driver: UsbSerialDriver,
        device: UsbDevice,
        portNumber: Int,
    ) : CommonUsbSerialPort(driver, device, portNumber) {

        private var dtr = false
        private var rts = false

        override fun openInt() {
            for (i in 0 until device.interfaceCount) {
                if (!connection!!.claimInterface(device.getInterface(i), true)) {
                    throw IOException("Could not claim data interface")
                }
            }
            val dataIface = device.getInterface(device.interfaceCount - 1)
            for (i in 0 until dataIface.endpointCount) {
                val ep = dataIface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) readEndpoint = ep else writeEndpoint = ep
                }
            }
            initialize()
            setBaudRate(DEFAULT_BAUD_RATE)
        }

        override fun closeInt() {
            runCatching { for (i in 0 until device.interfaceCount) connection!!.releaseInterface(device.getInterface(i)) }
        }

        private fun controlOut(request: Int, value: Int, index: Int): Int {
            val reqType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
            return connection!!.controlTransfer(reqType, request, value, index, null, 0, USB_TIMEOUT)
        }

        private fun controlIn(request: Int, value: Int, index: Int, buffer: ByteArray): Int {
            val reqType = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_IN
            return connection!!.controlTransfer(reqType, request, value, index, buffer, buffer.size, USB_TIMEOUT)
        }

        private fun checkState(msg: String, request: Int, value: Int, expected: IntArray) {
            val buffer = ByteArray(expected.size)
            val ret = controlIn(request, value, 0, buffer)
            if (ret < 0) throw IOException("Failed send cmd [$msg]")
            if (ret != expected.size) throw IOException("Expected ${expected.size} bytes, got $ret [$msg]")
            for (i in expected.indices) {
                if (expected[i] == -1) continue
                val current = buffer[i].toInt() and 0xff
                if (expected[i] != current) {
                    throw IOException("Expected 0x${expected[i].toString(16)} but got 0x${current.toString(16)} [$msg]")
                }
            }
        }

        private fun setControlLines() {
            val value = (if (dtr) SCL_DTR else 0) or (if (rts) SCL_RTS else 0)
            if (controlOut(0xa4, value.inv() and 0xff, 0) < 0) throw IOException("Failed to set control lines")
        }

        private fun status(): Int {
            val buffer = ByteArray(2)
            if (controlIn(0x95, 0x0706, 0, buffer) < 0) throw IOException("Error getting control lines")
            return buffer[0].toInt() and 0xff
        }

        private fun initialize() {
            checkState("init #1", 0x5f, 0, intArrayOf(-1, 0x00))
            if (controlOut(0xa1, 0, 0) < 0) throw IOException("Init failed: #2")
            setBaudRate(DEFAULT_BAUD_RATE)
            checkState("init #4", 0x95, 0x2518, intArrayOf(-1, 0x00))
            if (controlOut(0x9a, 0x2518, LCR_ENABLE_RX or LCR_ENABLE_TX or LCR_CS8) < 0) throw IOException("Init failed: #5")
            checkState("init #6", 0x95, 0x0706, intArrayOf(-1, -1))
            if (controlOut(0xa1, 0x501f, 0xd90a) < 0) throw IOException("Init failed: #7")
            setBaudRate(DEFAULT_BAUD_RATE)
            setControlLines()
            checkState("init #10", 0x95, 0x0706, intArrayOf(-1, -1))
        }

        private fun setBaudRate(baudRate: Int) {
            var factor: Long
            var divisor: Long
            if (baudRate == 921600) {
                divisor = 7
                factor = 0xf300
            } else {
                factor = 1532620800L / baudRate
                divisor = 3
                while (factor > 0xfff0 && divisor > 0) {
                    factor = factor shr 3
                    divisor--
                }
                if (factor > 0xfff0) throw UnsupportedOperationException("Unsupported baud rate: $baudRate")
                factor = 0x10000 - factor
            }
            divisor = divisor or 0x0080 // else ch341a waits until buffer full
            val val1 = ((factor and 0xff00) or divisor).toInt()
            val val2 = (factor and 0xff).toInt()
            if (controlOut(0x9a, 0x1312, val1) < 0) throw IOException("Error setting baud rate: #1")
            if (controlOut(0x9a, 0x0f2c, val2) < 0) throw IOException("Error setting baud rate: #2")
        }

        override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
            require(baudRate > 0) { "Invalid baud rate: $baudRate" }
            setBaudRate(baudRate)
            var lcr = LCR_ENABLE_RX or LCR_ENABLE_TX
            lcr = lcr or when (dataBits) {
                UsbSerialPort.DATABITS_5 -> LCR_CS5
                UsbSerialPort.DATABITS_6 -> LCR_CS6
                UsbSerialPort.DATABITS_7 -> LCR_CS7
                UsbSerialPort.DATABITS_8 -> LCR_CS8
                else -> throw IllegalArgumentException("Invalid data bits: $dataBits")
            }
            lcr = lcr or when (parity) {
                UsbSerialPort.PARITY_NONE -> 0
                UsbSerialPort.PARITY_ODD -> LCR_ENABLE_PAR
                UsbSerialPort.PARITY_EVEN -> LCR_ENABLE_PAR or LCR_PAR_EVEN
                UsbSerialPort.PARITY_MARK -> LCR_ENABLE_PAR or LCR_MARK_SPACE
                UsbSerialPort.PARITY_SPACE -> LCR_ENABLE_PAR or LCR_MARK_SPACE or LCR_PAR_EVEN
                else -> throw IllegalArgumentException("Invalid parity: $parity")
            }
            lcr = lcr or when (stopBits) {
                UsbSerialPort.STOPBITS_1 -> 0
                UsbSerialPort.STOPBITS_1_5 -> throw UnsupportedOperationException("Unsupported stop bits: 1.5")
                UsbSerialPort.STOPBITS_2 -> LCR_STOP_BITS_2
                else -> throw IllegalArgumentException("Invalid stop bits: $stopBits")
            }
            if (controlOut(0x9a, 0x2518, lcr) < 0) throw IOException("Error setting control byte")
        }

        // CH34x reports control lines active-low (bit == 0 means asserted).
        override fun getCD() = status() and GCL_CD == 0
        override fun getCTS() = status() and GCL_CTS == 0
        override fun getDSR() = status() and GCL_DSR == 0
        override fun getRI() = status() and GCL_RI == 0
        override fun getDTR() = dtr
        override fun getRTS() = rts

        override fun setDTR(value: Boolean) {
            dtr = value
            setControlLines()
        }
        override fun setRTS(value: Boolean) {
            rts = value
            setControlLines()
        }

        override fun getControlLines(): Set<ControlLine> {
            val s = status()
            return buildSet {
                if (rts) add(ControlLine.RTS)
                if (s and GCL_CTS == 0) add(ControlLine.CTS)
                if (dtr) add(ControlLine.DTR)
                if (s and GCL_DSR == 0) add(ControlLine.DSR)
                if (s and GCL_CD == 0) add(ControlLine.CD)
                if (s and GCL_RI == 0) add(ControlLine.RI)
            }
        }

        override fun getSupportedControlLines(): Set<ControlLine> = ControlLine.entries.toSet()

        override fun setBreak(value: Boolean) {
            val req = ByteArray(2)
            if (controlIn(0x95, 0x1805, 0, req) < 0) throw IOException("Error getting BREAK condition")
            if (value) {
                req[0] = (req[0].toInt() and 1.inv()).toByte()
                req[1] = (req[1].toInt() and 0x40.inv()).toByte()
            } else {
                req[0] = (req[0].toInt() or 1).toByte()
                req[1] = (req[1].toInt() or 0x40).toByte()
            }
            val v = ((req[1].toInt() and 0xff) shl 8) or (req[0].toInt() and 0xff)
            if (controlOut(0x9a, 0x1805, v) < 0) throw IOException("Error setting BREAK condition")
        }

        private companion object {
            const val USB_TIMEOUT = 5000
            const val DEFAULT_BAUD_RATE = 9600
            const val LCR_ENABLE_RX = 0x80
            const val LCR_ENABLE_TX = 0x40
            const val LCR_MARK_SPACE = 0x20
            const val LCR_PAR_EVEN = 0x10
            const val LCR_ENABLE_PAR = 0x08
            const val LCR_STOP_BITS_2 = 0x04
            const val LCR_CS8 = 0x03
            const val LCR_CS7 = 0x02
            const val LCR_CS6 = 0x01
            const val LCR_CS5 = 0x00
            const val GCL_CTS = 0x01
            const val GCL_DSR = 0x02
            const val GCL_RI = 0x04
            const val GCL_CD = 0x08
            const val SCL_DTR = 0x20
            const val SCL_RTS = 0x40
        }
    }

    /** Factory for the default prober. */
    object Factory : UsbSerialDriverFactory {
        override val supportedDevices: Map<Int, IntArray> = mapOf(
            UsbId.VENDOR_QINHENG to intArrayOf(UsbId.QINHENG_CH340, UsbId.QINHENG_CH341A),
        )

        override fun create(device: UsbDevice): UsbSerialDriver = Ch34xSerialDriver(device)
    }
}
