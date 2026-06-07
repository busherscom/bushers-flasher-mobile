package io.github.ajsb85.usbserial

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbRequest
import io.github.ajsb85.usbserial.TestSupport.bulkEndpoint
import io.github.ajsb85.usbserial.TestSupport.connection
import io.github.ajsb85.usbserial.TestSupport.device
import io.github.ajsb85.usbserial.TestSupport.intEndpoint
import io.github.ajsb85.usbserial.TestSupport.usbInterface
import io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver
import io.github.ajsb85.usbserial.driver.Ch34xSerialDriver
import io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mock the native UsbRequest used by CommonUsbSerialPort.open()/read(). */
@Suppress("DEPRECATION") // UsbRequest.queue(ByteBuffer, Int) mirrors the API the port uses
private fun mockUsbRequest() {
    mockkConstructor(UsbRequest::class)
    every { anyConstructed<UsbRequest>().initialize(any(), any()) } returns true
    every { anyConstructed<UsbRequest>().queue(any(), any()) } returns true
    every { anyConstructed<UsbRequest>().cancel() } returns true
}

class Cp21xxDriverTest {
    @BeforeTest fun setUp() = mockUsbRequest()

    @AfterTest fun tearDown() = unmockkAll()

    private fun openPort(): UsbSerialPort {
        val port = Cp21xxSerialDriver(device()).ports.first()
        port.open(connection())
        return port
    }

    @Test fun opensAndReportsState() {
        val port = openPort()
        assertTrue(port.isOpen)
        assertEquals("TESTSERIAL", port.serial)
        assertEquals(UsbConstants.USB_ENDPOINT_XFER_BULK, port.readEndpoint!!.type)
        port.close()
        assertFalse(port.isOpen)
    }

    @Test fun setParametersCoversAllBranches() {
        val port = openPort()
        for (db in intArrayOf(5, 6, 7, 8)) port.setParameters(9600, db, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        for (p in 0..4) port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, p)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE)
        assertFailsWith<IllegalArgumentException> { port.setParameters(0, 8, 1, 0) }
        assertFailsWith<IllegalArgumentException> { port.setParameters(9600, 9, 1, 0) }
        assertFailsWith<IllegalArgumentException> { port.setParameters(9600, 8, 9, 0) }
        assertFailsWith<UnsupportedOperationException> { port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1_5, 0) }
    }

    @Test fun controlLinesAndModem() {
        val port = openPort()
        port.setDTR(true)
        port.setRTS(true)
        assertTrue(port.getDTR())
        assertTrue(port.getRTS())
        assertTrue(port.getCD())
        assertTrue(port.getCTS())
        assertTrue(port.getDSR())
        assertTrue(port.getRI())
        assertEquals(UsbSerialPort.ControlLine.entries.toSet(), port.getControlLines())
        assertEquals(UsbSerialPort.ControlLine.entries.toSet(), port.getSupportedControlLines())
        port.purgeHwBuffers(true, true)
        port.setBreak(true)
        port.setBreak(false)
    }

    @Test fun readWriteOverBulk() {
        val port = openPort()
        assertEquals(8, port.read(ByteArray(8), 100))
        assertEquals(4, port.read(ByteArray(8), 4, 100))
        port.write(ByteArray(200) { it.toByte() }, 1000) // multi-chunk (> 64-byte packet)
        port.write(byteArrayOf(1, 2, 3), 0) // timeout 0 path
        // timeout==0 read path uses UsbRequest + requestWait
        assertEquals(0, port.read(ByteArray(8), 0))
    }

    @Test fun restrictedSecondPortRejectsUnsupported() {
        // Two interfaces -> port 1 is the "restricted" CP2105 second port.
        val dev = device(interfaces = listOf(usbInterface(0), usbInterface(1)))
        val port = Cp21xxSerialDriver(dev).ports[1]
        port.open(connection())
        assertFailsWith<UnsupportedOperationException> { port.setParameters(9600, 5, 1, 0) }
    }

    @Test fun controlTransferFailureThrows() {
        val conn = connection()
        every { conn.controlTransfer(any(), any(), any(), any(), any(), any(), any()) } returns 1 // non-zero = error
        val port = Cp21xxSerialDriver(device()).ports.first()
        assertFailsWith<java.io.IOException> { port.open(conn) }
    }
}

class Ch34xDriverTest {
    @BeforeTest fun setUp() = mockUsbRequest()

    @AfterTest fun tearDown() = unmockkAll()

    private fun openPort(status: Int = 0x00): UsbSerialPort {
        val dev = device(UsbId.VENDOR_QINHENG, UsbId.QINHENG_CH340)
        val port = Ch34xSerialDriver(dev).ports.first()
        port.open(connection(statusByte = status))
        return port
    }

    @Test fun opensInitializesAndSetsParams() {
        val port = openPort()
        assertTrue(port.isOpen)
        for (db in intArrayOf(5, 6, 7, 8)) port.setParameters(9600, db, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        for (p in 0..4) port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, p)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE)
        port.setParameters(921600, 8, 1, 0) // special divisor path
        assertFailsWith<UnsupportedOperationException> { port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1_5, 0) }
        port.close()
    }

    @Test fun controlLinesActiveLow() {
        val port = openPort(status = 0x00) // active-low: 0 bits => all asserted
        port.setDTR(true)
        port.setRTS(true)
        assertTrue(port.getCD())
        assertTrue(port.getCTS())
        assertTrue(port.getDSR())
        assertTrue(port.getRI())
        assertTrue(port.getControlLines().contains(UsbSerialPort.ControlLine.CTS))
        assertEquals(UsbSerialPort.ControlLine.entries.toSet(), port.getSupportedControlLines())
        port.setBreak(true)
    }
}

class CdcAcmDriverTest {
    @BeforeTest fun setUp() = mockUsbRequest()

    @AfterTest fun tearDown() = unmockkAll()

    private fun cdcDevice() = device(
        vendorId = 0x303A,
        productId = 0x1001,
        interfaces = listOf(
            usbInterface(id = 0, cls = UsbConstants.USB_CLASS_COMM, subclass = 2, endpoints = listOf(intEndpoint())),
            usbInterface(id = 1, cls = UsbConstants.USB_CLASS_CDC_DATA),
        ),
    )

    @Test fun probesByStructureAndOpens() {
        val dev = cdcDevice()
        assertTrue(CdcAcmSerialDriver.Factory.probe(dev))
        val port = CdcAcmSerialDriver(dev).ports.first()
        port.open(connection())
        assertTrue(port.isOpen)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.setParameters(115200, 7, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_EVEN)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1_5, UsbSerialPort.PARITY_ODD)
        port.setDTR(true)
        port.setRTS(true)
        assertTrue(port.getDTR())
        assertTrue(port.getRTS())
        assertEquals(setOf(UsbSerialPort.ControlLine.RTS, UsbSerialPort.ControlLine.DTR), port.getControlLines())
        port.setBreak(true)
        port.close()
    }

    @Test fun singleInterfaceAcmFallback() {
        // No COMM/CDC interfaces -> countPorts 0 -> single-interface (-1) logic.
        val combined = usbInterface(
            id = 0,
            cls = 0xFF,
            subclass = 0,
            endpoints = listOf(intEndpoint(), bulkEndpoint(true), bulkEndpoint(false)),
        )
        val dev = device(0x303A, 0x4001, interfaces = listOf(combined))
        val port = CdcAcmSerialDriver(dev).ports.first()
        port.open(connection())
        assertTrue(port.isOpen)
        port.close()
    }

    @Test fun unsupportedControlLinesThrow() {
        val port = CdcAcmSerialDriver(cdcDevice()).ports.first()
        port.open(connection())
        assertFailsWith<UnsupportedOperationException> { port.getCD() }
        assertFailsWith<UnsupportedOperationException> { port.purgeHwBuffers(true, true) }
        assertFailsWith<UnsupportedOperationException> { port.setFlowControl(UsbSerialPort.FlowControl.RTS_CTS) }
        port.setFlowControl(UsbSerialPort.FlowControl.NONE) // allowed
    }
}
