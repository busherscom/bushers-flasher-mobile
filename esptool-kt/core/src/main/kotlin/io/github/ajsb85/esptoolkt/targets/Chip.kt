package io.github.ajsb85.esptoolkt.targets

/** SPI peripheral register addresses used for flash-ID reads (per-chip, from `esp_targets.c`). */
data class SpiRegisters(
    val cmd: Long,
    val usr: Long,
    val usr1: Long,
    val usr2: Long,
    val w0: Long,
    val mosiDlen: Long,
    val misoDlen: Long,
) {
    companion object {
        /** Standard layout used by most ESP32xx chips: offsets relative to an SPI base. */
        fun standard(base: Long, w0Off: Long = 0x58, usrOff: Long = 0x18): SpiRegisters = SpiRegisters(
            cmd = base + 0x00,
            usr = base + usrOff,
            usr1 = base + usrOff + 0x04,
            usr2 = base + usrOff + 0x08,
            w0 = base + w0Off,
            mosiDlen = base + 0x24,
            misoDlen = base + 0x28,
        )
    }
}

/**
 * A supported ESP target. Values are ported directly from esp-serial-flasher's
 * `esp_targets.c` table and esptool.py per-chip target classes.
 */
data class Chip(
    val name: String,
    /** esptool-style identifier, e.g. "esp32s2" (used by `--chip`). */
    val id: String,
    /** `chip_id` reported by GET_SECURITY_INFO, or -1 if the chip predates that command. */
    val chipId: Int,
    /** Values that CHIP_DETECT_MAGIC_REG (0x40001000) may read back for this chip. */
    val magicValues: List<Long>,
    val efuseBase: Long,
    val macEfuseOffset: Long,
    val encryptionInBeginFlashCmd: Boolean,
    val spi: SpiRegisters,
    /** Flasher-stub resource name under `/stubs`, or null if none bundled. */
    val stubResource: String?,
)

/** Registry + detection logic mirroring `loader_detect_chip` / `target_from_chip_id`. */
object Chips {
    const val CHIP_DETECT_MAGIC_REG = 0x40001000L

    val ESP8266 = Chip(
        name = "ESP8266", id = "esp8266", chipId = -1,
        magicValues = listOf(0xfff0c101),
        efuseBase = 0, macEfuseOffset = 0, encryptionInBeginFlashCmd = false,
        spi = SpiRegisters(0x60000200, 0x6000021c, 0x60000220, 0x60000224, 0x60000240, 0, 0),
        stubResource = "stub_flasher_8266.json",
    )

    val ESP32 = Chip(
        name = "ESP32", id = "esp32", chipId = 0,
        magicValues = listOf(0x00f01d83),
        efuseBase = 0x3ff5A000, macEfuseOffset = 0x04, encryptionInBeginFlashCmd = false,
        spi = SpiRegisters(
            cmd = 0x3ff42000,
            usr = 0x3ff4201c,
            usr1 = 0x3ff42020,
            usr2 = 0x3ff42024,
            w0 = 0x3ff42080,
            mosiDlen = 0x3ff42028,
            misoDlen = 0x3ff4202c,
        ),
        stubResource = "stub_flasher_32.json",
    )

    val ESP32S2 = Chip(
        name = "ESP32-S2", id = "esp32s2", chipId = 2,
        magicValues = listOf(0x000007c6),
        efuseBase = 0x3f41A000, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x3f402000),
        stubResource = "stub_flasher_32s2.json",
    )

    val ESP32C3 = Chip(
        name = "ESP32-C3", id = "esp32c3", chipId = 5,
        magicValues = listOf(0x6921506f, 0x1b31506f, 0x4881606F, 0x4361606F),
        efuseBase = 0x60008800, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60002000),
        stubResource = "stub_flasher_32c3.json",
    )

    val ESP32S3 = Chip(
        name = "ESP32-S3", id = "esp32s3", chipId = 9,
        magicValues = listOf(0x00000009),
        efuseBase = 0x60007000, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60002000),
        stubResource = "stub_flasher_32s3.json",
    )

    val ESP32C2 = Chip(
        name = "ESP32-C2", id = "esp32c2", chipId = 12,
        magicValues = listOf(0x6f51306f, 0x7c41a06f, 0x0C21E06F),
        efuseBase = 0x60008800, macEfuseOffset = 0x40, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60002000),
        stubResource = "stub_flasher_32c2.json",
    )

    val ESP32C5 = Chip(
        name = "ESP32-C5", id = "esp32c5", chipId = 23,
        magicValues = listOf(0x1101406F, 0x5fd1406f),
        efuseBase = 0x600B4800, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60003000),
        stubResource = "stub_flasher_32c5.json",
    )

    val ESP32H2 = Chip(
        name = "ESP32-H2", id = "esp32h2", chipId = 16,
        magicValues = listOf(0xd7b73e80),
        efuseBase = 0x600B0800, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60003000),
        stubResource = "stub_flasher_32h2.json",
    )

    val ESP32C6 = Chip(
        name = "ESP32-C6", id = "esp32c6", chipId = 13,
        magicValues = listOf(0x2CE0806F),
        efuseBase = 0x600B0800, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60003000),
        stubResource = "stub_flasher_32c6.json",
    )

    val ESP32P4 = Chip(
        name = "ESP32-P4", id = "esp32p4", chipId = 18,
        magicValues = emptyList(),
        efuseBase = 0x5012d000, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x5008d000),
        stubResource = "stub_flasher_32p4.json",
    )

    val ESP32C61 = Chip(
        name = "ESP32-C61", id = "esp32c61", chipId = 20,
        magicValues = listOf(0x7211606F),
        efuseBase = 0x600B4800, macEfuseOffset = 0x44, encryptionInBeginFlashCmd = true,
        spi = SpiRegisters.standard(0x60003000),
        stubResource = "stub_flasher_32c61.json",
    )

    /** All chips in detection order. */
    val ALL: List<Chip> = listOf(
        ESP8266, ESP32, ESP32S2, ESP32C3, ESP32S3, ESP32C2,
        ESP32C5, ESP32H2, ESP32C6, ESP32P4, ESP32C61,
    )

    fun byMagic(magic: Long): Chip? = ALL.firstOrNull { chip -> chip.magicValues.any { it == (magic and 0xFFFFFFFFL) } }

    fun byChipId(chipId: Int): Chip? = ALL.firstOrNull { it.chipId == chipId }

    fun byId(id: String): Chip? = ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }
}
