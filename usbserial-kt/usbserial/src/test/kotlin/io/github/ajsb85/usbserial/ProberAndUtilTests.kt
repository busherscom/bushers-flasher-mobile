package io.github.ajsb85.usbserial

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import io.github.ajsb85.usbserial.TestSupport.bulkEndpoint
import io.github.ajsb85.usbserial.TestSupport.connection
import io.github.ajsb85.usbserial.TestSupport.device
import io.github.ajsb85.usbserial.TestSupport.intEndpoint
import io.github.ajsb85.usbserial.TestSupport.usbInterface
import io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver
import io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver
import io.github.ajsb85.usbserial.util.MonotonicClock
import io.github.ajsb85.usbserial.util.SerialInputOutputManager
import io.github.ajsb85.usbserial.util.UsbUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProbeTableAndProberTest {
    @AfterTest fun tearDown() = unmockkAll()

    @Test fun matchesByVidPid() {
        val table = UsbSerialProber.defaultProbeTable()
        val factory = table.findFactory(device(UsbId.VENDOR_SILABS, UsbId.SILABS_CP2102))
        assertNotNull(factory)
        assertTrue(UsbSerialProber(table).probeDevice(device(UsbId.VENDOR_SILABS, UsbId.SILABS_CP2102)) is Cp21xxSerialDriver)
    }

    @Test fun matchesCdcByStructure() {
        val dev = device(
            vendorId = 0x1234,
            productId = 0x5678,
            interfaces = listOf(
                usbInterface(0, UsbConstants.USB_CLASS_COMM, 2, listOf(intEndpoint())),
                usbInterface(1, UsbConstants.USB_CLASS_CDC_DATA),
            ),
        )
        assertTrue(UsbSerialProber.getDefaultProber().probeDevice(dev) is CdcAcmSerialDriver)
    }

    @Test fun returnsNullForUnknownDevice() {
        val dev = device(vendorId = 0x9999, productId = 0x0001, interfaces = listOf(usbInterface(0, 0x03, 0)))
        assertNull(UsbSerialProber.getDefaultProber().probeDevice(dev))
    }

    @Test fun findAllDriversScansManager() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.deviceList } returns hashMapOf("d0" to device(UsbId.VENDOR_QINHENG, UsbId.QINHENG_CH340))
        assertEquals(1, UsbSerial.availableDrivers(manager).size)
    }

    @Test fun usbSerialOpensPort() {
        mockkConstructor(UsbRequest::class)
        every { anyConstructed<UsbRequest>().initialize(any(), any()) } returns true
        every { anyConstructed<UsbRequest>().cancel() } returns true
        val manager = mockk<UsbManager>(relaxed = true)
        val driver = Cp21xxSerialDriver(device())
        every { manager.openDevice(any()) } returns connection()
        val port = UsbSerial.open(manager, driver, baudRate = 115200)
        assertTrue(port.isOpen)
        port.close()
    }

    @Test fun usbSerialOpenThrowsWhenNoPermission() {
        val manager = mockk<UsbManager>(relaxed = true)
        every { manager.openDevice(any()) } returns null
        assertTrue(
            runCatching { UsbSerial.open(manager, Cp21xxSerialDriver(device())) }.exceptionOrNull() is java.io.IOException,
        )
    }
}

class UtilTest {
    @Test fun monotonicClockNonDecreasing() {
        assertTrue(MonotonicClock.millis() <= MonotonicClock.millis())
    }

    @Test fun serialTimeoutCarriesTransferred() {
        val e = SerialTimeoutException("late", 7)
        assertEquals(7, e.bytesTransferred)
    }

    @Test fun descriptorsSplitByLength() {
        val conn = mockk<android.hardware.usb.UsbDeviceConnection>(relaxed = true)
        val raw = ByteArray(18).also { it[0] = 18 } + ByteArray(9).also { it[0] = 9 }
        every { conn.rawDescriptors } returns raw
        assertEquals(listOf(18, 9), UsbUtils.getDescriptors(conn).map { it.size })
    }

    @Test fun descriptorsEmptyWhenNull() {
        val conn = mockk<android.hardware.usb.UsbDeviceConnection>(relaxed = true)
        every { conn.rawDescriptors } returns null
        assertTrue(UsbUtils.getDescriptors(conn).isEmpty())
    }
}

class SerialInputOutputManagerTest {
    @Test fun servicesReadAndWrite() {
        val port = mockk<UsbSerialPort>(relaxed = true)
        every { port.readEndpoint } returns bulkEndpoint(true)
        every { port.isOpen } returns true
        every { port.read(any(), any()) } returns 4
        every { port.write(any(), any()) } returns Unit

        val gotData = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val manager = SerialInputOutputManager(
            port,
            object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    gotData.countDown()
                }
                override fun onRunError(e: Exception) {
                    errors.incrementAndGet()
                }
            },
        )
        manager.start()
        assertEquals(SerialInputOutputManager.State.RUNNING, manager.currentState)
        manager.writeAsync(byteArrayOf(1, 2, 3))
        assertTrue(gotData.await(2, TimeUnit.SECONDS), "expected onNewData")
        Thread.sleep(100)
        manager.stop()
        Thread.sleep(100)
        verify(atLeast = 1) { port.write(any(), any()) }
        assertEquals(0, errors.get())
    }
}
