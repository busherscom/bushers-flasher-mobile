package io.github.ajsb85.usbserial

import android.hardware.usb.UsbManager
import java.io.IOException

/** Convenience entry points for discovering and opening USB-serial ports. */
object UsbSerial {

    /** All compatible USB-serial drivers currently attached (no USB permission required). */
    fun availableDrivers(manager: UsbManager): List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

    /**
     * Open the first port of [driver] at [baudRate] 8N1. The app must already hold USB permission
     * for `driver.device` (see `UsbManager.requestPermission`).
     */
    @Throws(IOException::class)
    fun open(manager: UsbManager, driver: UsbSerialDriver, baudRate: Int = 115200): UsbSerialPort {
        val connection = manager.openDevice(driver.device)
            ?: throw IOException("Could not open USB device (permission not granted?)")
        val port = driver.ports.first()
        port.open(connection)
        port.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return port
    }
}
