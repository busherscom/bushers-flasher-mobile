package io.github.ajsb85.usbserial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import io.github.ajsb85.usbserial.CommonUsbSerialPort
import io.github.ajsb85.usbserial.EspressifUsb
import io.github.ajsb85.usbserial.UsbSerialDriver
import io.github.ajsb85.usbserial.UsbSerialDriverFactory
import io.github.ajsb85.usbserial.UsbSerialPort
import io.github.ajsb85.usbserial.UsbSerialPort.ControlLine
import io.github.ajsb85.usbserial.util.UsbUtils
import java.io.IOException

/**
 * USB CDC/ACM driver — used by ESP32-S2/S3/C3 **native USB** (no external UART bridge) and by
 * generic CDC-ACM devices. Matched structurally (interface class COMM/ACM + CDC-DATA) rather than
 * by vid/pid.
 */
class CdcAcmSerialDriver(override val device: UsbDevice) : UsbSerialDriver {

    override val ports: List<UsbSerialPort> = run {
        val count = countPorts(device)
        if (count == 0) {
            listOf(CdcAcmSerialPort(this, device, -1))
        } else {
            (0 until count).map { CdcAcmSerialPort(this, device, it) }
        }
    }

    private class CdcAcmSerialPort(
        driver: UsbSerialDriver,
        device: UsbDevice,
        portNumber: Int,
    ) : CommonUsbSerialPort(driver, device, portNumber) {

        private var controlInterface: UsbInterface? = null
        private var dataInterface: UsbInterface? = null
        private var controlIndex = 0
        private var dtr = false
        private var rts = false

        override fun openInt() {
            if (portNumber == -1) openSingleInterface() else openInterface()
        }

        private fun openSingleInterface() {
            val iface = device.getInterface(0)
            controlInterface = iface
            dataInterface = iface
            controlIndex = 0
            if (!connection!!.claimInterface(iface, true)) throw IOException("Could not claim shared control/data interface")
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                when {
                    ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK -> readEndpoint = ep
                    ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK -> writeEndpoint = ep
                }
            }
        }

        private fun openInterface() {
            val iad = interfaceIdFromDescriptors()
            if (iad >= 0) {
                for (i in 0 until device.interfaceCount) {
                    val usbIface = device.getInterface(i)
                    if (usbIface.id == iad || usbIface.id == iad + 1) {
                        if (usbIface.interfaceClass == UsbConstants.USB_CLASS_COMM && usbIface.interfaceSubclass == USB_SUBCLASS_ACM) {
                            controlIndex = usbIface.id
                            controlInterface = usbIface
                        }
                        if (usbIface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) dataInterface = usbIface
                    }
                }
            }
            if (controlInterface == null || dataInterface == null) {
                var controlCount = 0
                var dataCount = 0
                for (i in 0 until device.interfaceCount) {
                    val usbIface = device.getInterface(i)
                    if (usbIface.interfaceClass == UsbConstants.USB_CLASS_COMM && usbIface.interfaceSubclass == USB_SUBCLASS_ACM) {
                        if (controlCount == portNumber) {
                            controlIndex = usbIface.id
                            controlInterface = usbIface
                        }
                        controlCount++
                    }
                    if (usbIface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                        if (dataCount == portNumber) dataInterface = usbIface
                        dataCount++
                    }
                }
            }
            val control = controlInterface ?: throw IOException("No control interface")
            if (!connection!!.claimInterface(control, true)) throw IOException("Could not claim control interface")
            val data = dataInterface ?: throw IOException("No data interface")
            if (!connection!!.claimInterface(data, true)) throw IOException("Could not claim data interface")
            for (i in 0 until data.endpointCount) {
                val ep = data.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) readEndpoint = ep else writeEndpoint = ep
                }
            }
        }

        private fun interfaceIdFromDescriptors(): Int {
            val descriptors = UsbUtils.getDescriptors(connection!!)
            if (descriptors.isNotEmpty() &&
                descriptors[0].size == 18 &&
                descriptors[0][1].toInt() == 1 &&
                descriptors[0][4] == UsbConstants.USB_CLASS_MISC.toByte() &&
                descriptors[0][5].toInt() == 2 &&
                descriptors[0][6].toInt() == 1
            ) {
                var port = -1
                for (d in 1 until descriptors.size) {
                    val desc = descriptors[d]
                    if (desc.size == 8 &&
                        desc[1].toInt() == 0x0b &&
                        desc[4].toInt() == UsbConstants.USB_CLASS_COMM &&
                        desc[5].toInt() == USB_SUBCLASS_ACM
                    ) {
                        port++
                        if (port == portNumber && desc[3].toInt() == 2) return desc[2].toInt()
                    }
                }
            }
            return -1
        }

        private fun sendAcmControlMessage(request: Int, value: Int, buf: ByteArray?) {
            val len = connection!!.controlTransfer(USB_RT_ACM, request, value, controlIndex, buf, buf?.size ?: 0, 5000)
            if (len < 0) {
                System.err.println("CdcAcm controlTransfer warning: request $request failed")
            }
        }

        override fun closeInt() {
            runCatching { connection!!.releaseInterface(controlInterface) }
            runCatching { connection!!.releaseInterface(dataInterface) }
        }

        override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
            require(baudRate > 0) { "Invalid baud rate: $baudRate" }
            require(dataBits in UsbSerialPort.DATABITS_5..UsbSerialPort.DATABITS_8) { "Invalid data bits: $dataBits" }
            val stopByte = when (stopBits) {
                UsbSerialPort.STOPBITS_1 -> 0
                UsbSerialPort.STOPBITS_1_5 -> 1
                UsbSerialPort.STOPBITS_2 -> 2
                else -> throw IllegalArgumentException("Invalid stop bits: $stopBits")
            }
            val parityByte = when (parity) {
                UsbSerialPort.PARITY_NONE -> 0
                UsbSerialPort.PARITY_ODD -> 1
                UsbSerialPort.PARITY_EVEN -> 2
                UsbSerialPort.PARITY_MARK -> 3
                UsbSerialPort.PARITY_SPACE -> 4
                else -> throw IllegalArgumentException("Invalid parity: $parity")
            }
            val msg = byteArrayOf(
                (baudRate and 0xff).toByte(),
                ((baudRate shr 8) and 0xff).toByte(),
                ((baudRate shr 16) and 0xff).toByte(),
                ((baudRate shr 24) and 0xff).toByte(),
                stopByte.toByte(),
                parityByte.toByte(),
                dataBits.toByte(),
            )
            sendAcmControlMessage(SET_LINE_CODING, 0, msg)
        }

        override fun getDTR() = dtr
        override fun getRTS() = rts

        override fun setDTR(value: Boolean) {
            dtr = value
            setDtrRts()
        }

        override fun setRTS(value: Boolean) {
            rts = value
            setDtrRts()
        }

        private fun setDtrRts() {
            sendAcmControlMessage(SET_CONTROL_LINE_STATE, (if (rts) 0x2 else 0) or (if (dtr) 0x1 else 0), null)
        }

        override fun getControlLines(): Set<ControlLine> = buildSet {
            if (rts) add(ControlLine.RTS)
            if (dtr) add(ControlLine.DTR)
        }

        override fun getSupportedControlLines(): Set<ControlLine> = setOf(ControlLine.RTS, ControlLine.DTR)

        override fun setBreak(value: Boolean) = sendAcmControlMessage(SEND_BREAK, if (value) 0xffff else 0, null)

        private companion object {
            const val USB_SUBCLASS_ACM = 2
            const val USB_RECIP_INTERFACE = 0x01
            const val USB_RT_ACM = UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE
            const val SET_LINE_CODING = 0x20
            const val SET_CONTROL_LINE_STATE = 0x22
            const val SEND_BREAK = 0x23
        }
    }

    /**
     * Factory for the default prober. Espressif native-USB serial PIDs (VID 0x303A) are matched
     * explicitly for fast/reliable recognition; everything else is matched structurally
     * (interface class COMM/ACM + CDC-DATA), which also covers generic CDC and community ESP devices.
     */
    object Factory : UsbSerialDriverFactory {
        override val supportedDevices: Map<Int, IntArray> = mapOf(EspressifUsb.VENDOR_ID to EspressifUsb.SERIAL_PIDS)
        override fun probe(device: UsbDevice): Boolean = countPorts(device) > 0
        override fun create(device: UsbDevice): UsbSerialDriver = CdcAcmSerialDriver(device)
    }

    private companion object {
        const val USB_SUBCLASS_ACM = 2

        fun countPorts(device: UsbDevice): Int {
            var control = 0
            var data = 0
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM && iface.interfaceSubclass == USB_SUBCLASS_ACM) control++
                if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) data++
            }
            return minOf(control, data)
        }
    }
}
