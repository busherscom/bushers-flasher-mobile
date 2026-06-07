package io.github.ajsb85.esptoolkt.image

/**
 * Helpers for ESP application/bootloader images. An ESP image starts with the byte
 * `0xE9`; byte 2 encodes the flash mode and byte 3 packs `(sizeNibble << 4) | freq`.
 *
 * esptool rewrites these header bytes on whichever file is written at the chip's
 * bootloader offset so the running flash matches the requested mode/size/freq, without
 * needing the build to be regenerated. See esptool.py `_update_image_flash_params`.
 */
object FirmwareImage {
    const val ESP_IMAGE_MAGIC = 0xE9

    fun isEspImage(data: ByteArray): Boolean = data.isNotEmpty() && (data[0].toInt() and 0xFF) == ESP_IMAGE_MAGIC

    /**
     * Return a copy of [data] with flash params patched per [settings]. If [data] is not a
     * recognizable ESP image, or all settings are null, the original array is returned as-is.
     */
    fun patchFlashParams(data: ByteArray, settings: FlashSettings): ByteArray {
        if (!isEspImage(data) || data.size < 4) return data
        if (settings.mode == null && settings.size == null && settings.freq == null) return data

        val patched = data.copyOf()
        settings.mode?.let { patched[2] = it.code.toByte() }
        if (settings.size != null || settings.freq != null) {
            val current = patched[3].toInt() and 0xFF
            val sizeNibble = settings.size?.sizeNibble ?: (current shr 4)
            val freq = settings.freq?.code ?: (current and 0x0F)
            patched[3] = (((sizeNibble and 0x0F) shl 4) or (freq and 0x0F)).toByte()
        }
        return patched
    }

    /** Human-readable summary of an image header for the `image_info` command. */
    fun describe(data: ByteArray): String {
        if (!isEspImage(data)) return "Not an ESP image (first byte != 0xE9)"
        val segments = data[1].toInt() and 0xFF
        val modeCode = data[2].toInt() and 0xFF
        val sizeFreq = data[3].toInt() and 0xFF
        val entry = (data[4].toInt() and 0xFF) or
            ((data[5].toInt() and 0xFF) shl 8) or
            ((data[6].toInt() and 0xFF) shl 16) or
            ((data[7].toInt() and 0xFF) shl 24)
        val mode = FlashMode.entries.firstOrNull { it.code == modeCode }?.name ?: "0x%x".format(modeCode)
        val freq = FlashFreq.entries.firstOrNull { it.code == (sizeFreq and 0x0F) }?.label ?: "0x%x".format(sizeFreq and 0x0F)
        val size = FlashSize.entries.firstOrNull { it.sizeNibble == (sizeFreq shr 4) }?.label ?: "0x%x".format(sizeFreq shr 4)
        return buildString {
            appendLine("Image magic:    0xE9")
            appendLine("Segments:       $segments")
            appendLine("Flash mode:     $mode")
            appendLine("Flash size:     $size")
            appendLine("Flash freq:     $freq")
            append("Entry point:    0x%08x".format(entry))
        }
    }
}
