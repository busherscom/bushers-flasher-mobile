package io.github.ajsb85.usbserial

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import io.mockk.every
import io.mockk.mockk

/** Shared mockk builders for the USB host objects the drivers talk to. */
object TestSupport {

    fun bulkEndpoint(dirIn: Boolean): UsbEndpoint = mockk(relaxed = true) {
        every { type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { direction } returns if (dirIn) UsbConstants.USB_DIR_IN else UsbConstants.USB_DIR_OUT
        every { maxPacketSize } returns 64
    }

    fun intEndpoint(): UsbEndpoint = mockk(relaxed = true) {
        every { type } returns UsbConstants.USB_ENDPOINT_XFER_INT
        every { direction } returns UsbConstants.USB_DIR_IN
        every { maxPacketSize } returns 8
    }

    fun usbInterface(
        id: Int = 0,
        cls: Int = 0,
        subclass: Int = 0,
        endpoints: List<UsbEndpoint> = listOf(bulkEndpoint(true), bulkEndpoint(false)),
    ): UsbInterface = mockk(relaxed = true) {
        every { getId() } returns id
        every { interfaceClass } returns cls
        every { interfaceSubclass } returns subclass
        every { endpointCount } returns endpoints.size
        endpoints.forEachIndexed { i, ep -> every { getEndpoint(i) } returns ep }
    }

    fun device(
        vendorId: Int = UsbId.VENDOR_SILABS,
        productId: Int = UsbId.SILABS_CP2102,
        interfaces: List<UsbInterface> = listOf(usbInterface()),
    ): UsbDevice = mockk(relaxed = true) {
        every { getVendorId() } returns vendorId
        every { getProductId() } returns productId
        every { interfaceCount } returns interfaces.size
        every { deviceName } returns "/dev/bus/usb/001/00"
        every { deviceId } returns 1000
        interfaces.forEachIndexed { i, iface -> every { getInterface(i) } returns iface }
    }

    /**
     * A connection whose control transfers succeed: HOST_TO_DEVICE returns 0 (ok), and
     * DEVICE_TO_HOST fills the buffer with [statusByte] and returns its length.
     */
    fun connection(statusByte: Int = 0xFF): UsbDeviceConnection = mockk(relaxed = true) {
        every { claimInterface(any(), any()) } returns true
        every { releaseInterface(any()) } returns true
        every { serial } returns "TESTSERIAL"
        every { rawDescriptors } returns null
        every { requestWait() } returns mockk(relaxed = true)
        every { close() } returns Unit
        // bulkTransfer returns the requested length (full read/write).
        every { bulkTransfer(any<UsbEndpoint>(), any<ByteArray>(), any(), any()) } answers { arg(2) }
        every { controlTransfer(any(), any(), any(), any(), any(), any(), any()) } answers {
            val requestType = arg<Int>(0)
            val buffer = arg<ByteArray?>(4)
            val length = arg<Int>(5)
            if (requestType and 0x80 != 0 && buffer != null) { // DEVICE_TO_HOST: fill + return length
                for (i in buffer.indices) buffer[i] = statusByte.toByte()
                length
            } else {
                0 // HOST_TO_DEVICE success
            }
        }
    }
}
