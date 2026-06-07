package io.github.ajsb85.esptoolkt.image

/** SPI flash mode written into the image header (byte 2). */
enum class FlashMode(val code: Int) {
    QIO(0),
    QOUT(1),
    DIO(2),
    DOUT(3),
    ;

    companion object {
        fun from(s: String): FlashMode = entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
            ?: error("Unknown flash mode: $s")
    }
}

/** SPI flash clock frequency; the low nibble of image header byte 3. */
enum class FlashFreq(val label: String, val code: Int) {
    F20M("20m", 0x2),
    F26M("26m", 0x1),
    F40M("40m", 0x0),
    F80M("80m", 0xf),
    ;

    companion object {
        fun from(s: String): FlashFreq = entries.firstOrNull { it.label.equals(s, ignoreCase = true) }
            ?: error("Unknown flash frequency: $s")
    }
}

/**
 * SPI flash size. [sizeNibble] is the high nibble of image header byte 3; [bytes] is the
 * total capacity used for bounds checks and SPI_SET_PARAMS.
 */
enum class FlashSize(val label: String, val sizeNibble: Int, val bytes: Long) {
    // ESP32-family sizes first so nibble lookups resolve to these (256KB/512KB are
    // ESP8266-specific and reuse the same nibbles).
    S1MB("1MB", 0x0, 1L * 1024 * 1024),
    S2MB("2MB", 0x1, 2L * 1024 * 1024),
    S4MB("4MB", 0x2, 4L * 1024 * 1024),
    S8MB("8MB", 0x3, 8L * 1024 * 1024),
    S16MB("16MB", 0x4, 16L * 1024 * 1024),
    S32MB("32MB", 0x5, 32L * 1024 * 1024),
    S64MB("64MB", 0x6, 64L * 1024 * 1024),
    S128MB("128MB", 0x7, 128L * 1024 * 1024),
    S256KB("256KB", 0x1, 256L * 1024),
    S512KB("512KB", 0x0, 512L * 1024),
    ;

    companion object {
        fun from(s: String): FlashSize = entries.firstOrNull { it.label.equals(s, ignoreCase = true) }
            ?: error("Unknown flash size: $s")
    }
}

/**
 * The three flash parameters esptool patches into the bootloader image header before
 * writing. Any field left null means "leave the value already in the image unchanged".
 */
data class FlashSettings(
    val mode: FlashMode? = null,
    val size: FlashSize? = null,
    val freq: FlashFreq? = null,
)
