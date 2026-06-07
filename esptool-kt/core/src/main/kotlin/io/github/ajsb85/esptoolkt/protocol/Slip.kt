package io.github.ajsb85.esptoolkt.protocol

import io.github.ajsb85.esptoolkt.transport.SerialTransport
import java.io.ByteArrayOutputStream

/**
 * SLIP (RFC 1055) framing as used by the ESP serial bootloader protocol.
 *
 * Faithful port of esp-serial-flasher's `slip.c`:
 *  - `0xC0` is the frame delimiter,
 *  - `0xDB 0xDC` decodes to `0xC0`, `0xDB 0xDD` decodes to `0xDB`.
 */
object Slip {
    const val END = 0xC0
    const val ESC = 0xDB
    const val ESC_END = 0xDC
    const val ESC_ESC = 0xDD

    /** SLIP-encode [data] without delimiters (used by tests and bulk encoders). */
    fun encode(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size + 8)
        for (b in data) {
            when (val v = b.toInt() and 0xFF) {
                END -> {
                    out.write(ESC)
                    out.write(ESC_END)
                }
                ESC -> {
                    out.write(ESC)
                    out.write(ESC_ESC)
                }
                else -> out.write(v)
            }
        }
        return out.toByteArray()
    }

    /** Write a complete SLIP frame (`END` + encoded payload + `END`) in a single transport write. */
    fun writeFrame(transport: SerialTransport, payload: ByteArray) {
        val out = ByteArrayOutputStream(payload.size + 16)
        out.write(END)
        out.write(encode(payload))
        out.write(END)
        transport.write(out.toByteArray())
    }

    /**
     * Read and decode the next SLIP frame from [reader]. Handles the bootloader quirk
     * of emitting extra `0xC0` bytes (e.g. after a baud-rate change) by skipping leading
     * delimiters before the payload.
     */
    fun readFrame(reader: SerialReader): ByteArray {
        // Wait for the opening delimiter.
        var ch = reader.readByte()
        while (ch != END) ch = reader.readByte()
        // Skip any run of delimiters; `ch` ends up holding the first payload byte.
        do {
            ch = reader.readByte()
        } while (ch == END)

        val out = ByteArrayOutputStream(256)
        while (true) {
            when (ch) {
                ESC -> when (val n = reader.readByte()) {
                    ESC_END -> out.write(END)
                    ESC_ESC -> out.write(ESC)
                    else -> throw EspProtocolException("Invalid SLIP escape sequence: 0xDB 0x%02x".format(n))
                }
                END -> return out.toByteArray()
                else -> out.write(ch)
            }
            ch = reader.readByte()
        }
    }
}
