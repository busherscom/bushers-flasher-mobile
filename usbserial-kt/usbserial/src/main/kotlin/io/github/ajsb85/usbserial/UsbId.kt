package io.github.ajsb85.usbserial

/** Registry of USB vendor/product IDs relevant to ESP-class USB-serial bridges. */
object UsbId {
    const val VENDOR_FTDI = 0x0403
    const val FTDI_FT232R = 0x6001
    const val FTDI_FT231X = 0x6015

    const val VENDOR_SILABS = 0x10c4
    const val SILABS_CP2102 = 0xea60 // also CP2101/CP2103/CP2104/CP2109
    const val SILABS_CP2102C = 0xea64 // CP2102C with hardware flow control
    const val SILABS_CP2105 = 0xea70
    const val SILABS_CP2108 = 0xea71

    const val VENDOR_QINHENG = 0x1a86 // WCH
    const val QINHENG_CH340 = 0x7523
    const val QINHENG_CH341A = 0x5523

    /** Espressif native USB (ESP32-S2/S3/C3 USB-CDC). */
    const val VENDOR_ESPRESSIF = 0x303a
}
