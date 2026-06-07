package io.github.ajsb85.esptoolkt.android

import android.hardware.usb.UsbManager
import io.github.ajsb85.usbserial.UsbSerialDriver
import io.github.ajsb85.usbserial.UsbSerialPort
import io.github.ajsb85.usbserial.UsbSerialProber
import io.github.ajsb85.esptoolkt.transport.SerialTransport
import java.io.IOException

/**
 * Android [SerialTransport] backed by an open `usb-serial-for-android`
 * [UsbSerialPort]. This is the only Android-specific piece needed to run the entire
 * platform-agnostic `core` ([io.github.ajsb85.esptoolkt.EspTool] / `EspLoader`) on a phone over
 * USB-OTG — drivers such as the CP210x bridge on the ESP32-S2 are handled in user space, so no
 * root or kernel driver is required.
 *
 * Obtain a port via [UsbSerial.open] (after the app has been granted USB permission), then:
 * ```
 * EspTool(UsbSerialTransport(port), logger = myLogger).use { it.connect(); it.writeFlash(...) }
 * ```
 */
class UsbSerialTransport(
    private val port: UsbSerialPort,
    private val writeTimeoutMs: Int = 3000,
) : SerialTransport {

    private var currentBaud = 115200

    override var baudRate: Int
        get() = currentBaud
        set(value) {
            port.setParameters(value, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            currentBaud = value
        }

    override fun read(dst: ByteArray, length: Int, timeoutMs: Int): Int {
        val n = port.read(dst, length, timeoutMs)
        return if (n < 0) 0 else n
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        val buf = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        port.write(buf, length, writeTimeoutMs)
    }

    override fun setDtr(value: Boolean) = port.setDTR(value)
    override fun setRts(value: Boolean) = port.setRTS(value)

    override fun flushInput() {
        runCatching { port.purgeHwBuffers(false, true) }
    }

    override fun close() {
        runCatching { port.close() }
    }
}

/** Helpers for discovering and opening ESP-compatible USB-serial ports on Android. */
object UsbSerial {
    /** All USB-serial devices currently attached and recognized by usb-serial-for-android. */
    fun availableDrivers(manager: UsbManager): List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

    /**
     * Open the first port of [driver] at 115200 8N1. The app must already hold USB permission for
     * `driver.device` (see `UsbManager.requestPermission`), or [openDevice] returns null.
     */
    fun open(manager: UsbManager, driver: UsbSerialDriver): UsbSerialPort {
        val connection = manager.openDevice(driver.device)
            ?: throw IOException("Could not open USB device (permission not granted?)")
        val port = driver.ports.first()
        port.open(connection)
        port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return port
    }
}
