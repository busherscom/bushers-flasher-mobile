package io.github.ajsb85.usbserial

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbRequest
import io.github.ajsb85.usbserial.TestSupport.bulkEndpoint
import io.github.ajsb85.usbserial.TestSupport.connection
import io.github.ajsb85.usbserial.TestSupport.device
import io.github.ajsb85.usbserial.TestSupport.intEndpoint
import io.github.ajsb85.usbserial.TestSupport.usbInterface
import io.github.ajsb85.usbserial.driver.CdcAcmSerialDriver
import io.github.ajsb85.usbserial.util.SerialInputOutputManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdcAcmIadTest {
    @BeforeTest
    @Suppress("DEPRECATION")
    fun setUp() {
        mockkConstructor(UsbRequest::class)
        every { anyConstructed<UsbRequest>().initialize(any(), any()) } returns true
        every { anyConstructed<UsbRequest>().cancel() } returns true
    }

    @AfterTest fun tearDown() = unmockkAll()

    @Test fun opensViaIadDescriptor() {
        // Device descriptor (IAD-capable) + an Interface Association Descriptor for CDC/ACM.
        val devDesc = ByteArray(18).also {
            it[0] = 18
            it[1] = 1
            it[4] = UsbConstants.USB_CLASS_MISC.toByte()
            it[5] = 2
            it[6] = 1
        }
        val iad = ByteArray(8).also {
            it[0] = 8
            it[1] = 0x0b
            it[2] = 0
            it[3] = 2
            it[4] = UsbConstants.USB_CLASS_COMM.toByte()
            it[5] = 2
        }
        val conn = connection()
        every { conn.rawDescriptors } returns (devDesc + iad)

        val dev = device(
            vendorId = 0x303A,
            productId = 0x1001,
            interfaces = listOf(
                usbInterface(0, UsbConstants.USB_CLASS_COMM, 2, listOf(intEndpoint())),
                usbInterface(1, UsbConstants.USB_CLASS_CDC_DATA),
            ),
        )
        val port = CdcAcmSerialDriver(dev).ports.first()
        port.open(conn)
        assertTrue(port.isOpen)
        port.close()
    }
}

class SerialIoManagerErrorTest {
    @Test fun settersAndErrorListener() {
        val port = mockk<UsbSerialPort>(relaxed = true)
        every { port.readEndpoint } returns bulkEndpoint(true)
        every { port.isOpen } returns true
        every { port.read(any(), any()) } throws IOException("boom")

        val err = CountDownLatch(1)
        val manager = SerialInputOutputManager(port).apply {
            readTimeout = 100
            writeTimeout = 100
            readBufferSize = 128
            threadPriority = android.os.Process.THREAD_PRIORITY_DEFAULT
            listener = object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {}
                override fun onRunError(e: Exception) {
                    err.countDown()
                }
            }
        }
        assertEquals(128, manager.readBufferSize)
        manager.start()
        assertTrue(err.await(2, TimeUnit.SECONDS), "expected onRunError")
        manager.stop()
        Thread.sleep(50)
    }
}
