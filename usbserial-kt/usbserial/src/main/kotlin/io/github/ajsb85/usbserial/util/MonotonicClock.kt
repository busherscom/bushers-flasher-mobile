package io.github.ajsb85.usbserial.util

/** Monotonic millisecond clock, immune to wall-clock changes — used for all I/O timeouts. */
object MonotonicClock {
    fun millis(): Long = System.nanoTime() / 1_000_000L
}
