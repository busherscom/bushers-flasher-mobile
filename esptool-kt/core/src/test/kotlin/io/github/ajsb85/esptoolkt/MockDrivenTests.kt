package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.efuse.BurnConfirmation
import io.github.ajsb85.esptoolkt.efuse.EfuseController
import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.targets.Chips
import io.github.ajsb85.esptoolkt.transport.ClassicReset
import io.github.ajsb85.esptoolkt.transport.CustomReset
import io.github.ajsb85.esptoolkt.transport.HardReset
import io.github.ajsb85.esptoolkt.transport.ResetStrategy
import io.github.ajsb85.esptoolkt.transport.SerialTransport
import io.github.ajsb85.esptoolkt.transport.UsbJtagReset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val NO_RESET = ResetStrategy {}

private fun connectedLoader(stub: Boolean = true): Pair<EspLoader, MockEspChip> {
    val mock = MockEspChip()
    val loader = EspLoader(mock)
    loader.connect(NO_RESET)
    if (stub) loader.runStub()
    return loader to mock
}

class EspLoaderMockTest {
    @Test fun connectsAndDetectsEsp32s2() {
        val (loader, _) = connectedLoader(stub = false)
        assertEquals(Chips.ESP32S2, loader.chip)
    }

    @Test fun registerReadWrite() {
        val (loader, _) = connectedLoader(stub = false)
        loader.writeRegister(0x3FF00000, 0x12345678)
        assertEquals(0x12345678L, loader.readRegister(0x3FF00000))
    }

    @Test fun runsStubAndReportsOhai() {
        val (loader, _) = connectedLoader(stub = false)
        assertFalse(loader.stubRunning)
        assertTrue(loader.runStub())
        assertTrue(loader.stubRunning)
        assertTrue(loader.runStub()) // idempotent
    }

    @Test fun readsMac() {
        val (loader, _) = connectedLoader(stub = false)
        assertEquals("7c:df:a1:0e:d9:f8", loader.readMac().joinToString(":") { "%02x".format(it) })
    }

    @Test fun readsFlashIdAndSize() {
        val (loader, _) = connectedLoader()
        assertEquals(0x164020L, loader.readFlashId())
        assertEquals(4L * 1024 * 1024, loader.detectFlashSize())
    }

    @Test fun readsFlashStatus() {
        val (loader, _) = connectedLoader()
        assertEquals(0x0200L, loader.readFlashStatus(2))
    }

    @Test fun securityInfoReportsChipId() {
        val (loader, _) = connectedLoader(stub = false)
        assertEquals(2, loader.securityInfo()?.chipId)
    }

    @Test fun changesBaud() {
        val (loader, mock) = connectedLoader()
        loader.changeBaud(921600)
        assertEquals(921600, mock.baudRate)
    }

    @Test fun memoryHelpers() {
        val (loader, _) = connectedLoader(stub = false)
        loader.writeMem(0x3FFB0000, 0xCAFEBABE)
        assertEquals(0xCAFEBABEL, loader.readMem(0x3FFB0000))
        val dump = loader.dumpMem(0x3FFB0000, 4)
        assertEquals(4, dump.size)
    }

    @Test fun erasesFlashAndRegion() {
        val (loader, _) = connectedLoader()
        loader.eraseFlash()
        loader.eraseRegion(0x1000, 0x1000)
    }
}

class FlasherMockTest {
    @Test fun writeFlashCompressedVerifies() {
        val (loader, _) = connectedLoader()
        val data = ByteArray(9000) { (it % 97).toByte() }
        Flasher(loader).writeFlash(listOf(FlashSegment(0x10000, data)), compress = true, verify = true)
    }

    @Test fun writeFlashUncompressedVerifies() {
        val (loader, _) = connectedLoader()
        val data = ByteArray(5000) { (it % 13).toByte() }
        Flasher(loader).writeFlash(listOf(FlashSegment(0x20000, data)), compress = false, verify = true)
    }

    @Test fun verifyFlashMatches() {
        val (loader, _) = connectedLoader()
        val data = ByteArray(2048) { 0x5A }
        val flasher = Flasher(loader)
        flasher.writeFlash(listOf(FlashSegment(0x30000, data)), compress = false, verify = false)
        assertTrue(flasher.verifyFlash(0x30000, data))
    }
}

class EspToolMockTest {
    @Test fun facadeConnectFlashClose() {
        val mock = MockEspChip()
        EspTool(mock, before = NO_RESET, after = NO_RESET).use { tool ->
            assertEquals(Chips.ESP32S2, tool.connect())
            assertTrue(tool.connected)
            tool.writeFlash(listOf(FlashSegment(0x10000, ByteArray(1000) { it.toByte() })))
            assertEquals(2, tool.securityInfo()?.chipId)
            assertEquals(0x164020L, tool.flashId())
        }
    }
}

class EfuseMockTest {
    @Test fun summaryReads() {
        val (loader, _) = connectedLoader(stub = false)
        val text = EfuseController(loader).summary()
        assertTrue(text.contains("ESP32-S2"))
        assertTrue(text.contains("DIS_USB"))
    }

    @Test fun planRejectsBadInput() {
        val (loader, _) = connectedLoader(stub = false)
        val c = EfuseController(loader)
        assertTrue(c.plan("WR_DIS", 1).blocked != null)
        assertTrue(c.plan("DIS_USB", 2).blocked != null) // doesn't fit 1-bit field
        assertFailsWith<EspException> { c.plan("NOPE", 1) }
    }

    @Test fun burnSetsBitAndVerifies() {
        val (loader, _) = connectedLoader(stub = false)
        val c = EfuseController(loader)
        val plan = c.plan("DIS_USB", 1)
        assertTrue(plan.blocked == null)
        val result = c.burn(plan, BurnConfirmation.of("BURN"))
        assertTrue(result.verified)
        // After burning, the bit is set and cannot be cleared (one-way).
        assertTrue(c.plan("DIS_USB", 0).blocked != null)
    }

    @Test fun burnRefusesBlockedPlan() {
        val (loader, _) = connectedLoader(stub = false)
        val c = EfuseController(loader)
        val blocked = c.plan("WR_DIS", 1)
        assertFailsWith<EspException> { c.burn(blocked, BurnConfirmation.of("BURN")) }
    }

    @Test fun controllerRejectsNonS2() {
        // A loader that never connected has a null chip.
        val loader = EspLoader(MockEspChip())
        assertFailsWith<EspException> { EfuseController(loader) }
    }
}

/** Records DTR/RTS line changes so reset strategies can be unit-tested. */
private class RecordingTransport : SerialTransport {
    val events = mutableListOf<String>()
    override var baudRate: Int = 115200
    override fun read(dst: ByteArray, length: Int, timeoutMs: Int) = 0
    override fun write(data: ByteArray, offset: Int, length: Int) {}
    override fun setDtr(value: Boolean) {
        events += "DTR=$value"
    }
    override fun setRts(value: Boolean) {
        events += "RTS=$value"
    }
    override fun flushInput() {}
    override fun close() {}
}

class ResetStrategyTest {
    @Test fun classicResetTogglesLines() {
        val t = RecordingTransport()
        ClassicReset(resetDelayMs = 1).reset(t)
        assertTrue(t.events.contains("RTS=true"))
        assertTrue(t.events.last() == "DTR=false")
    }

    @Test fun hardResetPulsesRts() {
        val t = RecordingTransport()
        HardReset().reset(t)
        assertEquals(listOf("RTS=true", "RTS=false"), t.events)
    }

    @Test fun usbJtagResetRuns() {
        val t = RecordingTransport()
        UsbJtagReset().reset(t)
        assertTrue(t.events.isNotEmpty())
    }

    @Test fun customResetParsesSequence() {
        val t = RecordingTransport()
        CustomReset("D0|R1|W1|D1|R0").reset(t)
        assertEquals(listOf("DTR=false", "RTS=true", "DTR=true", "RTS=false"), t.events)
    }

    @Test fun customResetRejectsBadCommand() {
        assertFailsWith<IllegalStateException> { CustomReset("X9").reset(RecordingTransport()) }
    }
}

class FlashSettingsTest {
    @Test fun parsesAndRejects() {
        assertEquals(FlashMode.DIO, FlashMode.from("dio"))
        assertEquals(FlashSize.S2MB, FlashSize.from("2MB"))
        assertEquals(FlashFreq.F80M, FlashFreq.from("80m"))
        assertFailsWith<IllegalStateException> { FlashMode.from("zzz") }
    }

    @Test fun settingsDefaultsAreNull() {
        val s = FlashSettings()
        assertEquals(null, s.mode)
    }
}
