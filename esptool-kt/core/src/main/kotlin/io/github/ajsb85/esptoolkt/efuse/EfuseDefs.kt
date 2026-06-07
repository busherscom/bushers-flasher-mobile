package io.github.ajsb85.esptoolkt.efuse

/**
 * A single BLOCK0 eFuse field on the ESP32-S2 (positions from esptool's `esp32s2.yaml`).
 *
 * @param word index within BLOCK0's six read words
 * @param pos bit offset within that word
 * @param len bit width
 * @param wrDisBit the WR_DIS bit that write-protects this field, or null if not protectable
 */
data class EfuseField(
    val name: String,
    val word: Int,
    val pos: Int,
    val len: Int,
    val wrDisBit: Int?,
    val category: String,
    val description: String,
) {
    val mask: Long get() = (1L shl len) - 1
}

/** ESP32-S2 eFuse controller registers, timing, and the BLOCK0 field table. */
object EfuseDefsS2 {
    const val BASE = 0x3F41A000L
    const val PGM_DATA0 = BASE // 8 words of programming data
    const val CHECK_VALUE0 = BASE + 0x020
    const val CLK = BASE + 0x1C8
    const val CONF = BASE + 0x1CC
    const val STATUS = BASE + 0x1D0
    const val CMD = BASE + 0x1D4
    const val DAC_CONF = BASE + 0x1E8
    const val RD_TIM_CONF = BASE + 0x1EC
    const val WR_TIM_CONF0 = BASE + 0x1F0
    const val WR_TIM_CONF1 = BASE + 0x1F4
    const val WR_TIM_CONF2 = BASE + 0x1F8

    /** BLOCK0 read registers: six consecutive words starting here. */
    const val BLOCK0_RD = BASE + 0x02C
    const val BLOCK0_WORDS = 6

    const val WRITE_OP_CODE = 0x5A5AL
    const val READ_OP_CODE = 0x5AA5L
    const val PGM_CMD = 0x2L
    const val READ_CMD = 0x1L

    // Programming timing for a 40 MHz crystal (ESP32-S2 is always 40 MHz).
    const val TSUP_A = 0x1L
    const val TPGM = 0x190L
    const val THP_A = 0x1L
    const val TPGM_INACTIVE = 0x2L
    const val DAC_CLK_DIV = 0x50L
    const val PWR_ON_NUM = 0x5100L
    const val PWR_OFF_NUM = 0x80L

    /** Curated set of writable BLOCK0 security/config fields (esptool `show: y`). */
    val FIELDS: List<EfuseField> = listOf(
        EfuseField("WR_DIS", 0, 0, 32, null, "config", "Disables programming of individual eFuses (read-only here)"),
        EfuseField("RD_DIS", 1, 0, 7, 0, "config", "Disable reading from BLOCK4-10"),
        EfuseField("DIS_ICACHE", 1, 8, 1, 2, "config", "Disable Icache"),
        EfuseField("DIS_DCACHE", 1, 9, 1, 2, "config", "Disable Dcache"),
        EfuseField("DIS_DOWNLOAD_ICACHE", 1, 10, 1, 2, "config", "Disable Icache in download mode"),
        EfuseField("DIS_DOWNLOAD_DCACHE", 1, 11, 1, 2, "config", "Disable Dcache in download mode"),
        EfuseField("DIS_FORCE_DOWNLOAD", 1, 12, 1, 2, "security", "Disable forcing chip into download mode"),
        EfuseField("DIS_USB", 1, 13, 1, 2, "security", "Disable USB OTG"),
        EfuseField("DIS_TWAI", 1, 14, 1, 2, "config", "Disable TWAI (CAN) controller"),
        EfuseField("SOFT_DIS_JTAG", 1, 17, 1, 2, "security", "Software-disable JTAG"),
        EfuseField("HARD_DIS_JTAG", 1, 18, 1, 2, "security", "Hardware-disable JTAG (permanent)"),
        EfuseField("DIS_DOWNLOAD_MANUAL_ENCRYPT", 1, 19, 1, 2, "security", "Disable flash encryption in download mode"),
        EfuseField("SPI_BOOT_CRYPT_CNT", 2, 18, 3, 4, "security", "Flash-encryption enable counter (1/3 bits = enabled)"),
        EfuseField("SECURE_BOOT_KEY_REVOKE0", 2, 21, 1, 5, "security", "Revoke 1st secure boot key"),
        EfuseField("SECURE_BOOT_KEY_REVOKE1", 2, 22, 1, 6, "security", "Revoke 2nd secure boot key"),
        EfuseField("SECURE_BOOT_KEY_REVOKE2", 2, 23, 1, 7, "security", "Revoke 3rd secure boot key"),
        EfuseField("KEY_PURPOSE_0", 2, 24, 4, 8, "security", "Purpose of KEY0"),
        EfuseField("KEY_PURPOSE_1", 2, 28, 4, 9, "security", "Purpose of KEY1"),
        EfuseField("KEY_PURPOSE_2", 3, 0, 4, 10, "security", "Purpose of KEY2"),
        EfuseField("KEY_PURPOSE_3", 3, 4, 4, 11, "security", "Purpose of KEY3"),
        EfuseField("KEY_PURPOSE_4", 3, 8, 4, 12, "security", "Purpose of KEY4"),
        EfuseField("KEY_PURPOSE_5", 3, 12, 4, 13, "security", "Purpose of KEY5"),
        EfuseField("SECURE_BOOT_EN", 3, 20, 1, 15, "security", "Enable secure boot"),
        EfuseField("SECURE_BOOT_AGGRESSIVE_REVOKE", 3, 21, 1, 16, "security", "Enable aggressive secure boot key revocation"),
        EfuseField("FLASH_TPUW", 3, 28, 4, 18, "config", "Flash startup delay after power-up (ms/2)"),
        EfuseField("DIS_DOWNLOAD_MODE", 4, 0, 1, 18, "security", "Disable all download boot modes"),
        EfuseField("DIS_LEGACY_SPI_BOOT", 4, 1, 1, 18, "config", "Disable legacy SPI boot mode"),
        EfuseField("DIS_USB_DOWNLOAD_MODE", 4, 4, 1, 18, "security", "Disable USB download mode"),
        EfuseField("ENABLE_SECURITY_DOWNLOAD", 4, 5, 1, 18, "security", "Enable secure UART download mode"),
        EfuseField("UART_PRINT_CONTROL", 4, 6, 2, 18, "config", "Control UART boot-log printing"),
    )

    fun field(name: String): EfuseField? = FIELDS.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
