package io.github.ajsb85.usbserial

import java.io.InterruptedIOException

/**
 * Thrown when a serial [UsbSerialPort.write] does not complete within its timeout.
 * [bytesTransferred] holds the number of bytes sent before the timeout.
 */
class SerialTimeoutException(message: String, transferred: Int = 0) : InterruptedIOException(message) {
    init {
        bytesTransferred = transferred
    }
}
