package io.github.ajsb85.usbserial.util

import android.hardware.usb.UsbDeviceConnection

/** Helpers for parsing raw USB descriptors (used by CDC-ACM interface detection). */
object UsbUtils {
    /** Split the connection's raw descriptor blob into individual descriptors. */
    fun getDescriptors(connection: UsbDeviceConnection): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val raw = connection.rawDescriptors ?: return out
        var pos = 0
        while (pos < raw.size) {
            var len = raw[pos].toInt() and 0xFF
            if (len == 0) break
            if (pos + len > raw.size) len = raw.size - pos
            out.add(raw.copyOfRange(pos, pos + len))
            pos += len
        }
        return out
    }
}
