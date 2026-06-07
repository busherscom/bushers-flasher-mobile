package io.github.ajsb85.usbserial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver
import io.github.ajsb85.usbserial.driver.Ch34xSerialDriver
import io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver

/** Maps attached USB devices to a [UsbSerialDriverFactory] by vid/pid, then by structural probe. */
class ProbeTable {
    private val vidPid = LinkedHashMap<Long, UsbSerialDriverFactory>()
    private val probeFactories = ArrayList<UsbSerialDriverFactory>()

    private fun key(vendorId: Int, productId: Int): Long = (vendorId.toLong() shl 32) or productId.toLong()

    /** Register a driver factory's vid/pid pairs and its optional structural probe. */
    fun addDriver(factory: UsbSerialDriverFactory): ProbeTable {
        for ((vendorId, productIds) in factory.supportedDevices) {
            for (productId in productIds) vidPid[key(vendorId, productId)] = factory
        }
        probeFactories.add(factory)
        return this
    }

    fun findFactory(device: UsbDevice): UsbSerialDriverFactory? {
        vidPid[key(device.vendorId, device.productId)]?.let { return it }
        return probeFactories.firstOrNull { it.probe(device) }
    }
}

/**
 * Discovers compatible drivers for attached USB devices. Probing does not open devices, so it
 * needs no USB permission.
 */
class UsbSerialProber(private val probeTable: ProbeTable) {

    /** All compatible drivers among currently-attached devices (possibly empty). */
    fun findAllDrivers(usbManager: UsbManager): List<UsbSerialDriver> = usbManager.deviceList.values.mapNotNull { probeDevice(it) }

    /** Build a driver for [device], or null if none matches. */
    fun probeDevice(device: UsbDevice): UsbSerialDriver? = probeTable.findFactory(device)?.create(device)

    companion object {
        fun getDefaultProber(): UsbSerialProber = UsbSerialProber(defaultProbeTable())

        fun defaultProbeTable(): ProbeTable = ProbeTable().apply {
            addDriver(Cp21xxSerialDriver.Factory)
            addDriver(Ch34xSerialDriver.Factory)
            addDriver(CdcAcmSerialDriver.Factory)
        }
    }
}
