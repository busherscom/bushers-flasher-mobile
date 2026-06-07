package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.protocol.Bytes

/**
 * Parsed result of the GET_SECURITY_INFO command (`get_security_info_response_data_t`).
 *
 * Enterprise provisioning/update apps use this to report a device's security posture
 * (secure boot, flash encryption, JTAG/USB lockdown) before or after flashing.
 */
data class SecurityInfo(
    val secureBootEnabled: Boolean,
    val secureBootAggressiveRevoke: Boolean,
    val secureDownloadModeEnabled: Boolean,
    val secureBootKeyRevoked: BooleanArray,
    val jtagSoftwareDisabled: Boolean,
    val jtagHardwareDisabled: Boolean,
    val usbDisabled: Boolean,
    val downloadDcacheDisabled: Boolean,
    val downloadIcacheDisabled: Boolean,
    val flashEncryptionEnabled: Boolean,
    /** chip_id field, or null on chips (e.g. ESP32-S2) whose response omits it. */
    val chipId: Int?,
    /** eco_version (chip revision), or null when not reported. */
    val ecoVersion: Int?,
) {
    /** Chip revision formatted as `major.minor`, or "unknown". */
    val revision: String
        get() = ecoVersion?.let { "${it / 100}.${it % 100}" } ?: "unknown"

    fun summary(): String = buildString {
        appendLine("Secure boot:          ${if (secureBootEnabled) "enabled" else "disabled"}")
        appendLine("Flash encryption:     ${if (flashEncryptionEnabled) "enabled" else "disabled"}")
        appendLine("Secure download mode: ${if (secureDownloadModeEnabled) "enabled" else "disabled"}")
        appendLine("JTAG:                 ${if (jtagHardwareDisabled || jtagSoftwareDisabled) "disabled" else "enabled"}")
        appendLine("USB:                  ${if (usbDisabled) "disabled" else "enabled"}")
        append("Chip revision:        v$revision")
    }

    companion object {
        // Flag bits from GET_SECURITY_INFO_* in esp-serial-flasher's protocol.h.
        private const val SECURE_BOOT_EN = 1 shl 0
        private const val SECURE_BOOT_AGGRESSIVE_REVOKE = 1 shl 1
        private const val SECURE_DOWNLOAD_ENABLE = 1 shl 2
        private const val KEY_REVOKE0 = 1 shl 3
        private const val KEY_REVOKE1 = 1 shl 4
        private const val KEY_REVOKE2 = 1 shl 5
        private const val SOFT_DIS_JTAG = 1 shl 6
        private const val HARD_DIS_JTAG = 1 shl 7
        private const val DIS_USB = 1 shl 8
        private const val DIS_DOWNLOAD_DCACHE = 1 shl 9
        private const val DIS_DOWNLOAD_ICACHE = 1 shl 10

        /** Parse the raw response data of GET_SECURITY_INFO (12 or 20 bytes). */
        fun parse(data: ByteArray): SecurityInfo {
            val flags = Bytes.readU32(data, 0).toInt()
            // key_purposes occupy bytes 5..11; flash encryption = odd popcount (per esp-serial-flasher).
            var keyPurposeBits = 0
            for (i in 5..11) keyPurposeBits += Integer.bitCount(data[i].toInt() and 0xFF)
            val full = data.size >= 20
            return SecurityInfo(
                secureBootEnabled = flags and SECURE_BOOT_EN != 0,
                secureBootAggressiveRevoke = flags and SECURE_BOOT_AGGRESSIVE_REVOKE != 0,
                secureDownloadModeEnabled = flags and SECURE_DOWNLOAD_ENABLE != 0,
                secureBootKeyRevoked = booleanArrayOf(
                    flags and KEY_REVOKE0 != 0,
                    flags and KEY_REVOKE1 != 0,
                    flags and KEY_REVOKE2 != 0,
                ),
                jtagSoftwareDisabled = flags and SOFT_DIS_JTAG != 0,
                jtagHardwareDisabled = flags and HARD_DIS_JTAG != 0,
                usbDisabled = flags and DIS_USB != 0,
                downloadDcacheDisabled = flags and DIS_DOWNLOAD_DCACHE != 0,
                downloadIcacheDisabled = flags and DIS_DOWNLOAD_ICACHE != 0,
                flashEncryptionEnabled = keyPurposeBits % 2 == 1,
                chipId = if (full) Bytes.readU32(data, 12).toInt() else null,
                ecoVersion = if (full) Bytes.readU32(data, 16).toInt() else null,
            )
        }
    }

    // Generated equals/hashCode would warn on the BooleanArray fields; we don't compare these.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
