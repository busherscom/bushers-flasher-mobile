package io.github.ajsb85.usbserial

import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import io.github.ajsb85.usbserial.TestSupport.connection
import io.github.ajsb85.usbserial.TestSupport.device
import io.github.ajsb85.usbserial.driver.Cp21xxSerialDriver
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the CommonUsbSerialPort base behavior via a concrete (CP210x) port. */
class CommonPortTests {
    @BeforeTest
    @Suppress("DEPRECATION")
    fun setUp() {
        mockkConstructor(UsbRequest::class)
        every { anyConstructed<UsbRequest>().initialize(any(), any()) } returns true
        every { anyConstructed<UsbRequest>().queue(any(), any()) } returns true
        every { anyConstructed<UsbRequest>().cancel() } returns true
    }

    @AfterTest fun tearDown() = unmockkAll()

    private fun openPort(conn: android.hardware.usb.UsbDeviceConnection = connection()): UsbSerialPort = Cp21xxSerialDriver(device()).ports.first().also { it.open(conn) }

    @Test fun doubleOpenAndDoubleCloseRejected() {
        val port = openPort()
        assertFailsWith<IOException> { port.open(connection()) }
        port.close()
        assertFailsWith<IOException> { port.close() }
    }

    @Test fun readValidatesArguments() {
        val port = openPort()
        assertFailsWith<IllegalArgumentException> { port.read(ByteArray(0), 100) }
        assertFailsWith<IllegalArgumentException> { port.read(ByteArray(8), 0, 100) }
    }

    @Test fun writeTimeoutThrowsSerialTimeout() {
        val conn = connection()
        val port = openPort(conn)
        every { conn.bulkTransfer(any<UsbEndpoint>(), any<ByteArray>(), any(), any()) } returns -1
        val e = assertFailsWith<SerialTimeoutException> { port.write(ByteArray(10), 1000) }
        assertEquals(0, e.bytesTransferred)
    }

    @Test fun writeNoTimeoutThrowsIO() {
        val conn = connection()
        val port = openPort(conn)
        every { conn.bulkTransfer(any<UsbEndpoint>(), any<ByteArray>(), any(), any()) } returns -1
        assertFailsWith<IOException> { port.write(ByteArray(10), 0) }
    }

    @Test fun readReturnsZeroWhenBulkTransferFails() {
        val conn = connection()
        val port = openPort(conn)
        every { conn.bulkTransfer(any<UsbEndpoint>(), any<ByteArray>(), any(), any()) } returns -1
        assertEquals(0, port.read(ByteArray(8), 100)) // -1 -> testConnection ok -> 0
    }

    @Test fun readThrowsWhenConnectionProbeFails() {
        val conn = connection()
        val port = openPort(conn)
        every { conn.bulkTransfer(any<UsbEndpoint>(), any<ByteArray>(), any(), any()) } returns -1
        // GET_STATUS (DEVICE_TO_HOST 0x80) now reports a dead link.
        every { conn.controlTransfer(0x80, any(), any(), any(), any(), any(), any()) } returns -1
        assertFailsWith<IOException> { port.read(ByteArray(8), 100) }
    }
}
