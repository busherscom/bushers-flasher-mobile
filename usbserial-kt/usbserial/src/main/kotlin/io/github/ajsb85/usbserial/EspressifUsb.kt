package io.github.ajsb85.usbserial

import android.hardware.usb.UsbDevice

/**
 * Registry and identification for **Espressif native-USB** devices (USB-IF VID `0x303A`).
 *
 * Espressif ships a single registered VID for all native-USB chips; the PID tells you the mode.
 * This helper lets an app recognize the device family/mode and decide whether it is in a serial
 * (flashable) state. Data condensed from the official
 * [espressif/usb-pids](https://github.com/espressif/usb-pids) registry — one VID covers ~900 PIDs,
 * so fixed modes are enumerated and the devkit/community ranges are matched by range.
 */
object EspressifUsb {
    /** Espressif's USB-IF registered vendor id. */
    const val VENDOR_ID = 0x303A

    /** Hardware/stack mode implied by the product id. */
    enum class Mode {
        USB_SERIAL_JTAG, // 0x1001 — S3/C3/C5/C6/H2 hardware USB-Serial-JTAG
        USB_OTG_ROM, // 0x0002 — ESP32-S2 ROM USB-OTG (DFU)
        HS_ROM, // 0x1007 — ESP32-P4 high-speed ROM bootloader
        TINYUSB_CDC, // 0x0011 — CDC console
        TINYUSB_COMPOSITE, // 0x0010 — CDC + MSC
        TINYUSB_MSC, // 0x0012 — mass storage (not serial)
        TINYUSB_HID, // 0x0013 — HID (not serial)
        DEVKIT_BOOTLOADER, // 0x7xxx — UF2 / CircuitPython devkit bootloaders
        COMMUNITY_RUNTIME, // 0x8xxx — Arduino / CircuitPython / MicroPython runtimes
        UNKNOWN,
    }

    /** Identification of an Espressif native-USB device. */
    data class Info(val productId: Int, val label: String, val mode: Mode, val exposesSerial: Boolean)

    private val fixed: Map<Int, Info> = mapOf(
        0x0002 to Info(0x0002, "ESP32-S2 USB-OTG ROM bootloader (DFU)", Mode.USB_OTG_ROM, true),
        0x0010 to Info(0x0010, "TinyUSB CDC + MSC composite", Mode.TINYUSB_COMPOSITE, true),
        0x0011 to Info(0x0011, "TinyUSB CDC console", Mode.TINYUSB_CDC, true),
        0x0012 to Info(0x0012, "TinyUSB MSC mass storage", Mode.TINYUSB_MSC, false),
        0x0013 to Info(0x0013, "TinyUSB HID", Mode.TINYUSB_HID, false),
        0x1001 to Info(0x1001, "USB-Serial-JTAG (S3/C3/C5/C6/H2)", Mode.USB_SERIAL_JTAG, true),
        0x1007 to Info(0x1007, "ESP32-P4 high-speed ROM bootloader", Mode.HS_ROM, true),
    )

    /**
     * Fixed PIDs that present a serial (CDC) interface usable for flashing/console. Registered in
     * the prober so these are matched by vid/pid directly, in addition to structural CDC probing.
     */
    val SERIAL_PIDS: IntArray = intArrayOf(0x0002, 0x0010, 0x0011, 0x1001, 0x1007)

    fun isEspressif(vendorId: Int): Boolean = vendorId == VENDOR_ID
    fun isEspressif(device: UsbDevice): Boolean = isEspressif(device.vendorId)

    /** Identify a product id (only meaningful for VID 0x303A). */
    fun identify(productId: Int): Info = fixed[productId] ?: when (productId) {
        in 0x7000..0x7FFF -> Info(productId, "Espressif devkit UF2/CircuitPython bootloader", Mode.DEVKIT_BOOTLOADER, false)
        in 0x8000..0x8FFF -> Info(productId, "Espressif community runtime device", Mode.COMMUNITY_RUNTIME, false)
        else -> Info(productId, "Espressif native-USB device", Mode.UNKNOWN, false)
    }

    /** Human-readable label for [device], or null if it is not an Espressif native-USB device. */
    fun describe(device: UsbDevice): String? = if (isEspressif(device)) identify(device.productId).label else null
}
