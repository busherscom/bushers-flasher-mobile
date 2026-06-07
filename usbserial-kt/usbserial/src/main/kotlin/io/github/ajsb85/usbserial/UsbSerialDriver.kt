package io.github.ajsb85.usbserial

import android.hardware.usb.UsbDevice

/**
 * A driver bound to one [UsbDevice], exposing one or more [UsbSerialPort]s.
 */
interface UsbSerialDriver {
    /** The backing device. */
    val device: UsbDevice

    /** All ports for this device (never empty). */
    val ports: List<UsbSerialPort>
}

/**
 * Factory describing which devices a driver supports and how to build it.
 *
 * Unlike the original Java library — which discovered drivers via reflection on static methods —
 * the Kotlin port registers explicit factory objects. This is more robust under R8/ProGuard
 * (no kept-method requirements) and is the recommended pattern for enterprise builds.
 */
interface UsbSerialDriverFactory {
    /** Map of USB vendor id → supported product ids for a fast vid/pid match. */
    val supportedDevices: Map<Int, IntArray>

    /**
     * Optional structural probe for devices not identified by vid/pid alone (e.g. CDC-ACM).
     * Default: matches only via [supportedDevices].
     */
    fun probe(device: UsbDevice): Boolean = false

    /** Build a driver for [device]. Only called after a successful match. */
    fun create(device: UsbDevice): UsbSerialDriver
}
