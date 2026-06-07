package io.github.ajsb85.usbserial

import io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver
import io.github.ajsb85.usbserial.driver.Ch34xSerialDriver
import io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver
import io.github.ajsb85.usbserial.util.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hardware-free unit tests for the driver registry and helpers. */
class ProberTest {

    @Test fun cp210xFactoryAdvertisesSilabsIds() {
        val ids = Cp21xxSerialDriver.Factory.supportedDevices
        assertContains(ids.keys, UsbId.VENDOR_SILABS)
        assertTrue(ids.getValue(UsbId.VENDOR_SILABS).contains(UsbId.SILABS_CP2102))
    }

    @Test fun ch34xFactoryAdvertisesQinhengIds() {
        val ids = Ch34xSerialDriver.Factory.supportedDevices
        assertTrue(ids.getValue(UsbId.VENDOR_QINHENG).contains(UsbId.QINHENG_CH340))
    }

    @Test fun cdcAcmAdvertisesEspressifSerialPidsAndProbes() {
        // CDC-ACM matches generic devices structurally, and Espressif serial PIDs explicitly.
        assertContains(CdcAcmSerialDriver.Factory.supportedDevices.keys, EspressifUsb.VENDOR_ID)
    }

    @Test fun monotonicClockIsMonotonic() {
        val a = MonotonicClock.millis()
        val b = MonotonicClock.millis()
        assertTrue(b >= a)
    }

    @Test fun parityConstants() {
        assertEquals(0, UsbSerialPort.PARITY_NONE)
        assertEquals(8, UsbSerialPort.DATABITS_8)
    }

    @Test fun espressifVidAndFixedPids() {
        assertEquals(0x303A, EspressifUsb.VENDOR_ID)
        assertTrue(EspressifUsb.isEspressif(0x303A))
        assertTrue(!EspressifUsb.isEspressif(UsbId.VENDOR_SILABS))
        assertEquals(EspressifUsb.Mode.USB_SERIAL_JTAG, EspressifUsb.identify(0x1001).mode)
        assertTrue(EspressifUsb.identify(0x1001).exposesSerial)
        assertTrue(!EspressifUsb.identify(0x0013).exposesSerial) // HID is not serial
    }

    @Test fun espressifRangeIdentification() {
        assertEquals(EspressifUsb.Mode.DEVKIT_BOOTLOADER, EspressifUsb.identify(0x7002).mode)
        assertEquals(EspressifUsb.Mode.COMMUNITY_RUNTIME, EspressifUsb.identify(0x8006).mode)
        assertEquals(EspressifUsb.Mode.UNKNOWN, EspressifUsb.identify(0x9999).mode)
    }

    @Test fun cdcAcmFactoryMatchesEspressifSerialPids() {
        val pids = CdcAcmSerialDriver.Factory.supportedDevices[EspressifUsb.VENDOR_ID]
        assertTrue(pids != null && pids.contains(0x1001))
    }
}
