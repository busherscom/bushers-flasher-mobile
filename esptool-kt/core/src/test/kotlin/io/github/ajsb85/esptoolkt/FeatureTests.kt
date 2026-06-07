package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.image.EspImage
import io.github.ajsb85.esptoolkt.image.MergeBin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeBinTest {
    @Test fun fillsGapsWith0xFF() {
        val merged = MergeBin.mergeRaw(
            listOf(
                FlashSegment(0x0, byteArrayOf(0x01, 0x02)),
                FlashSegment(0x5, byteArrayOf(0x09)),
            ),
        )
        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x09),
            merged,
        )
    }

    @Test fun rejectsOverlap() {
        try {
            MergeBin.mergeRaw(
                listOf(FlashSegment(0x0, ByteArray(4)), FlashSegment(0x2, ByteArray(4))),
            )
            error("expected overlap to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Overlap"))
        }
    }
}

class EspImageParseTest {
    @Test fun parsesEsp32ImageWithExtendedHeader() {
        val image = byteArrayOf(
            0xE9.toByte(), 0x01, 0x02, 0x1f, // magic, 1 seg, mode, size/freq
            0x00, 0x00, 0x08, 0x40, // entry 0x40080000
            // extended header (16 bytes): wp, spi_drv(3), chip_id(2)=9, rev fields, hash=0
            0x00, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // segment: addr 0x3ffb0000, len 4, data
            0x00, 0x00, 0xfb.toByte(), 0x3f, 0x04, 0x00, 0x00, 0x00,
            0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(),
        )
        val parsed = EspImage.parse(image, hasExtendedHeader = true)
        assertEquals(0x40080000L, parsed.entry)
        assertEquals(9, parsed.chipId)
        assertEquals(1, parsed.segments.size)
        assertEquals(0x3ffb0000L, parsed.segments[0].address)
        assertContentEquals(byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte()), parsed.segments[0].data)
    }
}

class SecurityInfoTest {
    @Test fun parsesFlagsAndRevision() {
        val data = byteArrayOf(
            0x01, 0x00, 0x00, 0x00, // flags: secure boot enabled
            0x00, // flash_crypt_cnt
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // key_purposes[7]
            0x09, 0x00, 0x00, 0x00, // chip_id = 9
            0x64, 0x00, 0x00, 0x00, // eco_version = 100
        )
        val info = SecurityInfo.parse(data)
        assertTrue(info.secureBootEnabled)
        assertTrue(!info.flashEncryptionEnabled)
        assertEquals(9, info.chipId)
        assertEquals("1.0", info.revision)
    }
}
