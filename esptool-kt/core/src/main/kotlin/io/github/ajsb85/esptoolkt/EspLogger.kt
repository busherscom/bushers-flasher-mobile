package io.github.ajsb85.esptoolkt

/** Minimal sink for human-facing progress/status, so the core stays UI-agnostic. */
interface EspLogger {
    fun info(message: String) {}
    fun detail(message: String) {}

    /** Progress callback for long operations; [done] and [total] are in bytes. */
    fun progress(label: String, done: Long, total: Long) {}
}

/** Discards everything. Used as the default when the caller does not supply a logger. */
object NoopLogger : EspLogger
