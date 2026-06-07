package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.FirmwareImage
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import io.github.ajsb85.esptoolkt.protocol.Command
import io.github.ajsb85.esptoolkt.protocol.Response
import io.github.ajsb85.esptoolkt.protocol.SerialReader
import io.github.ajsb85.esptoolkt.protocol.Slip
import io.github.ajsb85.esptoolkt.stub.FlasherStub
import io.github.ajsb85.esptoolkt.targets.Chips
import io.github.ajsb85.esptoolkt.transport.SerialTransport
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** In-memory transport that replays a fixed byte stream for SLIP decode tests. */
private class FakeTransport(private val data: ByteArray) : SerialTransport {
    private var pos = 0
    override var baudRate: Int = 115200
    override fun read(dst: ByteArray, length: Int, timeoutMs: Int): Int {
        if (pos >= data.size) return 0
        val n = minOf(length, data.size - pos)
        data.copyInto(dst, 0, pos, pos + n)
        pos += n
        return n
    }
    override fun write(data: ByteArray, offset: Int, length: Int) {}
    override fun setDtr(value: Boolean) {}
    override fun setRts(value: Boolean) {}
    override fun flushInput() {}
    override fun close() {}
}

class SlipTest {
    @Test fun encodesSpecialBytes() {
        assertContentEquals(byteArrayOf(0xDB.toByte(), 0xDC.toByte()), Slip.encode(byteArrayOf(0xC0.toByte())))
        assertContentEquals(byteArrayOf(0xDB.toByte(), 0xDD.toByte()), Slip.encode(byteArrayOf(0xDB.toByte())))
        assertContentEquals(byteArrayOf(0x01, 0x02), Slip.encode(byteArrayOf(0x01, 0x02)))
    }

    @Test fun decodesFrameWithEscapesAndLeadingDelimiters() {
        val payload = byteArrayOf(0x01, 0xC0.toByte(), 0xDB.toByte(), 0x55)
        // frame = C0 C0 (extra) + encoded payload + C0
        val frame = byteArrayOf(0xC0.toByte(), 0xC0.toByte()) + Slip.encode(payload) + byteArrayOf(0xC0.toByte())
        val reader = SerialReader(FakeTransport(frame))
        reader.startTimer(1000)
        assertContentEquals(payload, Slip.readFrame(reader))
    }
}

class CommandTest {
    @Test fun checksumUsesEspMagicSeed() {
        assertEquals(0xEF, Command.checksum(ByteArray(0)))
        assertEquals(0xEF xor 0x10 xor 0x20, Command.checksum(byteArrayOf(0x10, 0x20)))
    }

    @Test fun parsesResponseValueAndStatus() {
        // dir=1 op=0x0a size=2 value=0xdeadbeef data=<none> status=00 00
        val frame = byteArrayOf(
            0x01, 0x0A, 0x02, 0x00,
            0xEF.toByte(), 0xBE.toByte(), 0xAD.toByte(), 0xDE.toByte(),
            0x00, 0x00,
        )
        val r = Response.parse(frame)
        assertEquals(0x0A, r.command)
        assertEquals(0xDEADBEEFL, r.value)
        assertTrue(!r.failed)
    }
}

class ImageTest {
    @Test fun patchesFlashModeAndSizeFreqByte() {
        val image = byteArrayOf(0xE9.toByte(), 0x02, 0x00, 0x00) + ByteArray(8)
        val patched = FirmwareImage.patchFlashParams(
            image,
            FlashSettings(FlashMode.DIO, FlashSize.S2MB, FlashFreq.F80M),
        )
        assertEquals(0x02, patched[2].toInt() and 0xFF) // DIO
        assertEquals((0x1 shl 4) or 0xF, patched[3].toInt() and 0xFF) // 2MB | 80m
    }

    @Test fun leavesNonEspImageUntouched() {
        val notImage = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertContentEquals(notImage, FirmwareImage.patchFlashParams(notImage, FlashSettings(FlashMode.QIO)))
    }
}

class FlasherTest {
    @Test fun deflateRoundTrips() {
        val original = ByteArray(4096) { (it * 31 % 251).toByte() }
        val compressed = Flasher.deflate(original)
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArray(original.size)
        val n = inflater.inflate(out)
        inflater.end()
        assertEquals(original.size, n)
        assertContentEquals(original, out)
    }

    @Test fun md5HexKnownVector() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Flasher.md5Hex(ByteArray(0)))
    }
}

class StubTest {
    @Test fun esp32s2StubDecodes() {
        val stub = FlasherStub.forChip(Chips.ESP32S2)
        assertNotNull(stub)
        assertTrue(stub.entry > 0)
        assertTrue(stub.segments.isNotEmpty())
        assertTrue(stub.segments.all { it.data.isNotEmpty() })
    }
}

class ChipTest {
    @Test fun detectsEsp32s2ByMagic() {
        assertEquals(Chips.ESP32S2, Chips.byMagic(0x000007c6))
    }

    @Test fun lookupByChipId() {
        assertEquals(Chips.ESP32S2, Chips.byChipId(2))
    }
}
