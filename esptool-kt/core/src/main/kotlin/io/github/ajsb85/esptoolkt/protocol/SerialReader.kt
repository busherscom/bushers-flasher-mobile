package io.github.ajsb85.esptoolkt.protocol

import io.github.ajsb85.esptoolkt.transport.SerialTransport

/**
 * Buffered, deadline-aware byte reader over a [SerialTransport].
 *
 * The ESP SLIP decoder consumes the input one byte at a time (see esp-serial-flasher's
 * `slip.c`); to avoid a syscall per byte this reader fills an internal buffer in bulk
 * and hands out bytes from it. A single deadline ([startTimer]) governs how long a
 * whole command/response exchange may take, matching the `start_timer` / `remaining_time`
 * pattern of the reference C port layer.
 */
class SerialReader(
    private val transport: SerialTransport,
    bufferSize: Int = 8192,
) {
    private val buf = ByteArray(bufferSize)
    private var pos = 0
    private var len = 0
    private var deadlineNanos = 0L

    /** Arm the read deadline for the next exchange. */
    fun startTimer(timeoutMs: Int) {
        deadlineNanos = System.nanoTime() + timeoutMs.toLong() * 1_000_000L
    }

    private fun remainingMs(): Int {
        val r = (deadlineNanos - System.nanoTime()) / 1_000_000L
        return r.coerceAtLeast(1L).toInt()
    }

    /** Read a single byte (0..255), throwing [EspTimeoutException] if the deadline passes. */
    fun readByte(): Int {
        if (pos >= len) {
            len = transport.read(buf, buf.size, remainingMs())
            pos = 0
            if (len <= 0) throw EspTimeoutException()
        }
        return buf[pos++].toInt() and 0xFF
    }

    /** Drop any buffered data and the underlying input queue. */
    fun flush() {
        pos = 0
        len = 0
        transport.flushInput()
    }
}
