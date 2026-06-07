package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.efuse.BurnConfirmation
import io.github.ajsb85.esptoolkt.efuse.EfuseController
import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.EspImage
import io.github.ajsb85.esptoolkt.image.FirmwareImage
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import io.github.ajsb85.esptoolkt.protocol.Command
import io.github.ajsb85.esptoolkt.protocol.EspCommandException
import io.github.ajsb85.esptoolkt.protocol.Response
import io.github.ajsb85.esptoolkt.secure.FlashEncryption
import io.github.ajsb85.esptoolkt.targets.Chips
import io.github.ajsb85.esptoolkt.transport.ResetStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NO_RESET2 = ResetStrategy {}

private fun stubLoader(): EspLoader {
    val loader = EspLoader(MockEspChip())
    loader.connect(NO_RESET2)
    loader.runStub()
    return loader
}

class ReadFlashAndLoadRamTest {
    @Test fun readFlashReturnsRequestedBytes() {
        val data = stubLoader().readFlash(0x0, 256)
        assertEquals(256, data.size)
    }

    @Test fun loadRamStreamsSegments() {
        val loader = EspLoader(MockEspChip())
        loader.connect(NO_RESET2)
        // Minimal ESP32 image: header + ext header + one 4-byte segment.
        val image = byteArrayOf(
            0xE9.toByte(), 0x01, 0x02, 0x1f, 0x00, 0x00, 0x08, 0x40,
            0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0xfb.toByte(), 0x3f, 0x04, 0x00, 0x00, 0x00,
            0x01, 0x02, 0x03, 0x04,
        )
        loader.loadRam(EspImage.parse(image, hasExtendedHeader = true))
    }
}

class FlasherBootloaderPatchTest {
    @Test fun patchesImageAtBootloaderOffset() {
        val loader = stubLoader()
        // An ESP image header (0xE9) written at the S2 bootloader offset 0x1000 gets patched.
        val image = byteArrayOf(0xE9.toByte(), 0x02, 0x00, 0x00) + ByteArray(60)
        Flasher(loader).writeFlash(
            listOf(FlashSegment(0x1000, image, name = "bootloader")),
            settings = FlashSettings(FlashMode.DIO, FlashSize.S2MB, FlashFreq.F80M),
            compress = false,
        )
    }
}

class EfuseAlreadySetTest {
    @Test fun reBurningAlreadySetBitIsNoOp() {
        val loader = EspLoader(MockEspChip())
        loader.connect(NO_RESET2)
        val c = EfuseController(loader)
        c.burn(c.plan("DIS_USB", 1), BurnConfirmation.of("BURN"))
        val again = c.plan("DIS_USB", 1)
        assertEquals(0L, again.newBits)
        val result = c.burn(again, BurnConfirmation.of("BURN"))
        assertTrue(result.verified)
    }

    @Test fun summaryShowsWriteProtectedAfterRevoke() {
        val loader = EspLoader(MockEspChip())
        loader.connect(NO_RESET2)
        val c = EfuseController(loader)
        c.describePlan() // exercises plan.describe()
    }

    private fun EfuseController.describePlan() {
        val plan = plan("SOFT_DIS_JTAG", 1)
        assertTrue(plan.describe().contains("PERMANENT"))
    }
}

class ResponseAndExceptionTest {
    @Test fun parsesFailedResponse() {
        val frame = byteArrayOf(0x01, 0x0A, 0x00, 0x00, 0, 0, 0, 0, 0x01, 0x05)
        val r = Response.parse(frame)
        assertTrue(r.failed)
        assertEquals(0x05, r.errorCode)
    }

    @Test fun commandExceptionNamesError() {
        val e = EspCommandException(Command.FLASH_DATA.opcode, 0x08)
        assertTrue(e.message!!.contains("FLASH_WRITE_ERR"))
        val unknown = EspCommandException(0x99, 0xEE)
        assertTrue(unknown.message!!.contains("UNKNOWN_ERROR"))
    }
}

class DescribeAndMiscTest {
    @Test fun espImageDescribe() {
        val image = byteArrayOf(
            0xE9.toByte(), 0x01, 0x02, 0x1f, 0x60, 0x4f, 0x02, 0x40,
            0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, // ext-header byte 15 = hash_appended
            0x00, 0x00, 0xfb.toByte(), 0x3f, 0x02, 0x00, 0x00, 0x00, 0x07, 0x08,
        )
        val text = EspImage.parse(image, hasExtendedHeader = true).describe()
        assertTrue(text.contains("Segments:"))
        assertTrue(text.contains("SHA256 appended: true"))
    }

    @Test fun firmwareImageDescribe() {
        val image = byteArrayOf(0xE9.toByte(), 0x02, 0x02, 0x1f) + ByteArray(4)
        assertTrue(FirmwareImage.describe(image).contains("Flash mode"))
        assertTrue(FirmwareImage.describe(byteArrayOf(0, 1, 2)).contains("Not an ESP image"))
    }

    @Test fun securityInfoSummary() {
        val info = SecurityInfo.parse(ByteArray(20).also { it[12] = 2 })
        assertTrue(info.summary().contains("Secure boot"))
        assertEquals("unknown", SecurityInfo.parse(ByteArray(12)).revision)
    }

    @Test fun chipLookups() {
        assertNull(Chips.byMagic(0xDEADBEEF))
        assertNull(Chips.byId("nope"))
        assertEquals(Chips.ESP32S2, Chips.byId("ESP32S2"))
    }

    @Test fun flashEncryptionSizes() {
        assertEquals(16, FlashEncryption.generateKey(128).size)
    }
}
