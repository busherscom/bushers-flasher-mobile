package io.github.ajsb85.esptoolkt.image

import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import java.io.ByteArrayOutputStream

/**
 * Host-side merging of multiple `(offset, image)` pieces into a single binary, exactly as
 * esptool's `merge_bin` does. Especially handy for Android apps, which can then ship and flash
 * one firmware asset (`0x0 merged.bin`) instead of juggling bootloader/partition/app files.
 */
object MergeBin {
    /**
     * Merge [segments] into one gap-filled (`0xFF`) raw image starting at the lowest offset.
     * If [targetOffset] is given, output starts there; bytes before the first segment are padded.
     * Bootloader flash-param patching is applied per [settings].
     */
    fun mergeRaw(
        segments: List<FlashSegment>,
        settings: FlashSettings = FlashSettings(),
        bootloaderOffset: Long = 0x1000,
        targetOffset: Long? = null,
    ): ByteArray {
        require(segments.isNotEmpty()) { "merge_bin needs at least one segment" }
        val sorted = segments.sortedBy { it.offset }
        val base = targetOffset ?: sorted.first().offset
        val end = sorted.maxOf { it.offset + it.data.size }
        val out = ByteArrayOutputStream((end - base).toInt())
        var cursor = base
        for (seg in sorted) {
            require(seg.offset >= cursor) { "Overlapping segments at 0x%x".format(seg.offset) }
            repeat((seg.offset - cursor).toInt()) { out.write(0xFF) }
            val data = if (seg.offset == bootloaderOffset) {
                FirmwareImage.patchFlashParams(seg.data, settings)
            } else {
                seg.data
            }
            out.write(data)
            cursor = seg.offset + data.size
        }
        return out.toByteArray()
    }
}
