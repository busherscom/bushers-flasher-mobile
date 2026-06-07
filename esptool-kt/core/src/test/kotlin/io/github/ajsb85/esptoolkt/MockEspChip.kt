package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.protocol.Slip
import io.github.ajsb85.esptoolkt.transport.SerialTransport
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater

/**
 * An in-memory [SerialTransport] that emulates an ESP32-S2 ROM + flasher stub well enough to
 * drive the real [EspLoader] / [io.github.ajsb85.esptoolkt.flasher.Flasher] /
 * [io.github.ajsb85.esptoolkt.efuse.EfuseController] code paths in unit tests without hardware.
 *
 * It understands the SLIP request frames the loader sends and produces valid response frames:
 * SYNC, register read/write, GET_SECURITY_INFO (reports chip-id 2 = S2), the MEM_* stub upload
 * (replying `OHAI`), FLASH(/DEFL)_* with a real MD5 computed over the (de)compressed payload, the
 * SPI-register dance for flash-ID/status, and the eFuse controller program/read cycle (which
 * actually ORs programmed bits into the BLOCK0 read registers, mirroring one-way fuses).
 */
class MockEspChip : SerialTransport {
    override var baudRate: Int = 115200

    private val out = ByteArrayOutputStream()
    private var outPos = 0

    private val regs = HashMap<Long, Long>()
    var stubRunning = false
        private set

    // Flash write accounting for MD5 verification.
    private var flashOffset = 0L
    private var deflate = false
    private val payload = ByteArrayOutputStream()

    private var lastSpiCmd = 0

    init {
        // Chip-detect magic + a plausible MAC and zeroed efuse/SPI-config words.
        regs[0x40001000L] = 0x000007c6L
        regs[EFUSE_BASE + 0x44] = 0xA10ED9F8L // MAC part 1
        regs[EFUSE_BASE + 0x48] = 0x00007CDFL // MAC part 2 (+ spi-config word18 -> pins 0)
    }

    // --- SerialTransport ---

    override fun read(dst: ByteArray, length: Int, timeoutMs: Int): Int {
        val buf = out.toByteArray()
        if (outPos >= buf.size) return 0
        val n = minOf(length, buf.size - outPos)
        System.arraycopy(buf, outPos, dst, 0, n)
        outPos += n
        return n
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        val frame = data.copyOfRange(offset, offset + length)
        // Each loader write is exactly one SLIP frame: C0 <escaped> C0.
        val start = frame.indexOfFirst { it.toInt() and 0xFF == Slip.END }
        val endIdx = frame.indexOfLast { it.toInt() and 0xFF == Slip.END }
        if (start < 0 || endIdx <= start) return
        val body = unescape(frame.copyOfRange(start + 1, endIdx))
        if (body.size >= 8) process(body)
    }

    override fun setDtr(value: Boolean) {}
    override fun setRts(value: Boolean) {}
    override fun flushInput() {}
    override fun close() {}

    // --- emulation ---

    private fun process(req: ByteArray) {
        val op = req[1].toInt() and 0xFF
        val data = req.copyOfRange(8, req.size)
        when (op) {
            0x08 -> respond(op) // SYNC
            0x0A -> respond(op, value = regs.getOrDefault(u32(data, 0), 0L)) // READ_REG
            0x09 -> {
                writeReg(u32(data, 0), u32(data, 4))
                respond(op)
            } // WRITE_REG
            0x14 -> respond(op, data = securityInfo()) // GET_SECURITY_INFO
            0x05 -> respond(op) // MEM_BEGIN
            0x07 -> respond(op) // MEM_DATA
            0x06 -> {
                respond(op)
                enqueue("OHAI".toByteArray())
                stubRunning = true
            } // MEM_END
            0x0D, 0x0B -> respond(op) // SPI_ATTACH / SPI_SET_PARAMS
            0x0F -> respond(op) // CHANGE_BAUDRATE
            0xD0, 0xD1 -> respond(op) // ERASE_FLASH / ERASE_REGION
            0x02 -> {
                flashOffset = u32(data, 12)
                deflate = false
                payload.reset()
                respond(op)
            } // FLASH_BEGIN
            0x10 -> {
                flashOffset = u32(data, 12)
                deflate = true
                payload.reset()
                respond(op)
            } // FLASH_DEFL_BEGIN
            0x03, 0x11 -> {
                payload.write(data, 16, data.size - 16)
                respond(op)
            } // FLASH(/DEFL)_DATA
            0x04, 0x12 -> respond(op) // FLASH(/DEFL)_END
            0x13 -> respond(op, data = md5(u32(data, 4))) // SPI_FLASH_MD5(addr, size)
            0xD2 -> { // READ_FLASH_STUB(addr, length, packet, inflight)
                val length = u32(data, 4).toInt()
                respond(op)
                enqueue(ByteArray(length) { it.toByte() }) // one data packet
                enqueue(ByteArray(16)) // trailing MD5 frame
            }
            else -> respond(op)
        }
    }

    private fun writeReg(addr: Long, value: Long) {
        regs[addr] = value
        when (addr) {
            SPI_USR2 -> lastSpiCmd = (value and 0xFF).toInt()
            SPI_CMD -> if (value and (1L shl 18) != 0L) {
                regs[SPI_W0] = spiResult(lastSpiCmd)
                regs[SPI_CMD] = 0 // command complete
            }
            EFUSE_CMD -> {
                if (value and 0x2L != 0L) { // PGM_CMD: OR programming data into BLOCK0 (one-way fuses)
                    for (i in 0 until 6) {
                        val a = EFUSE_BASE + 0x2C + i * 4
                        regs[a] = (regs.getOrDefault(a, 0L)) or regs.getOrDefault(EFUSE_BASE + i * 4, 0L)
                    }
                }
                regs[EFUSE_CMD] = 0 // controller idle
            }
        }
    }

    private fun spiResult(cmd: Int): Long = when (cmd) {
        0x9F -> 0x164020L // JEDEC ID: manuf 0x20, size byte 0x16 -> 4 MB
        0x35 -> 0x02L // status register 2
        else -> 0x00L
    }

    private fun securityInfo(): ByteArray {
        val b = ByteArray(20)
        b[12] = 2 // chip_id = ESP32-S2
        return b
    }

    private fun md5(size: Long): ByteArray {
        val raw = payload.toByteArray()
        val content = if (deflate) inflate(raw) else raw
        val digest = MessageDigest.getInstance("MD5").digest(content.copyOf(size.toInt()))
        return if (stubRunning) digest else hex(digest).toByteArray()
    }

    // --- framing helpers ---

    private fun respond(op: Int, value: Long = 0, data: ByteArray = ByteArray(0)) {
        val body = ByteArrayOutputStream()
        body.write(0x01) // RESPONSE
        body.write(op)
        body.write(data.size and 0xFF)
        body.write((data.size shr 8) and 0xFF)
        for (i in 0 until 4) body.write(((value shr (i * 8)) and 0xFF).toInt())
        body.write(data)
        body.write(0)
        body.write(0) // status: ok
        enqueue(body.toByteArray())
    }

    private fun enqueue(body: ByteArray) {
        out.write(Slip.END)
        out.write(Slip.encode(body))
        out.write(Slip.END)
    }

    private fun unescape(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            val v = b[i].toInt() and 0xFF
            if (v == Slip.ESC && i + 1 < b.size) {
                val n = b[i + 1].toInt() and 0xFF
                o.write(if (n == Slip.ESC_END) Slip.END else Slip.ESC)
                i += 2
            } else {
                o.write(v)
                i++
            }
        }
        return o.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val o = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0) break
            o.write(buf, 0, n)
        }
        inf.end()
        return o.toByteArray()
    }

    private fun u32(b: ByteArray, off: Int): Long = (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
        ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    companion object {
        const val EFUSE_BASE = 0x3F41A000L
        const val EFUSE_CMD = EFUSE_BASE + 0x1D4
        const val SPI_BASE = 0x3F402000L
        const val SPI_CMD = SPI_BASE + 0x00
        const val SPI_USR2 = SPI_BASE + 0x20
        const val SPI_W0 = SPI_BASE + 0x58
    }
}
