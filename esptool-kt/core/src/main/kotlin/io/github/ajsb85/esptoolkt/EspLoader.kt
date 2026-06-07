package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.protocol.Bytes
import io.github.ajsb85.esptoolkt.protocol.Command
import io.github.ajsb85.esptoolkt.protocol.EspChipException
import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.protocol.EspProtocolException
import io.github.ajsb85.esptoolkt.protocol.EspTimeoutException
import io.github.ajsb85.esptoolkt.protocol.PayloadBuilder
import io.github.ajsb85.esptoolkt.protocol.Protocol
import io.github.ajsb85.esptoolkt.protocol.SerialReader
import io.github.ajsb85.esptoolkt.protocol.Slip
import io.github.ajsb85.esptoolkt.stub.FlasherStub
import io.github.ajsb85.esptoolkt.targets.Chip
import io.github.ajsb85.esptoolkt.targets.Chips
import io.github.ajsb85.esptoolkt.transport.ResetStrategy
import io.github.ajsb85.esptoolkt.transport.SerialTransport

/**
 * The ESP serial bootloader protocol engine — the Kotlin equivalent of esptool.py's
 * `ESPLoader` and esp-serial-flasher's `esp_loader.c`. It is platform-agnostic: it speaks
 * only through a [SerialTransport] and [ResetStrategy], so the same instance works on the
 * JVM (jSerialComm) and on Android (usb-serial-for-android).
 *
 * Typical lifecycle:
 * ```
 * loader.connect(ClassicReset())   // reset into bootloader + sync + chip detection
 * loader.runStub()                 // optional: upload the fast flasher stub
 * loader.changeBaud(460800)        // optional
 * // ... flash / read / erase via the primitives below, usually through Flasher ...
 * loader.hardReset(HardReset())    // reboot into the application
 * ```
 */
class EspLoader(
    val transport: SerialTransport,
    val logger: EspLogger = NoopLogger,
) {
    private val reader = SerialReader(transport)
    private val protocol = Protocol(transport, reader)

    var chip: Chip? = null
        private set
    var stubRunning: Boolean = false
        private set

    private var spiAttached = false
    private var flashSizeBytes: Long = 0
    private var sequence = 0

    /** Enable a hex wire trace (forwarded to [EspLogger.detail]). */
    fun enableTrace() {
        protocol.trace = { logger.detail(it) }
    }

    // region connection ---------------------------------------------------------------

    private val syncPayload: ByteArray = byteArrayOf(
        0x07,
        0x07,
        0x12,
        0x20,
    ) + ByteArray(32) { 0x55 }

    /**
     * Reset into the bootloader (via [before]) and synchronize, then detect the chip.
     * Retries the whole reset+sync cycle [attempts] times, like esptool's connect loop.
     */
    fun connect(before: ResetStrategy, attempts: Int = 7) {
        logger.info("Connecting...")
        var lastError: Exception? = null
        repeat(attempts) {
            try {
                before.reset(transport)
            } catch (e: Exception) {
                lastError = e
            }
            reader.flush()
            repeat(5) {
                if (trySync()) {
                    detectChip()
                    logger.info("Chip is ${chip?.name ?: "unknown"}")
                    return
                }
            }
        }
        throw EspTimeoutException("Failed to connect to ESP chip" + (lastError?.let { ": ${it.message}" } ?: ""))
    }

    private fun trySync(): Boolean = try {
        reader.startTimer(SYNC_TIMEOUT_MS)
        protocol.write(Command.SYNC, syncPayload)
        protocol.readResponse(Command.SYNC)
        drainPendingResponses()
        true
    } catch (_: EspException) {
        false
    }

    /** The bootloader emits several SYNC replies; consume the extras so they don't desync us. */
    private fun drainPendingResponses() {
        repeat(7) {
            try {
                reader.startTimer(50)
                Slip.readFrame(reader)
            } catch (_: EspTimeoutException) {
                return
            }
        }
    }

    /** Identify the chip via GET_SECURITY_INFO, then the chip-detect magic register. */
    fun detectChip() {
        securityInfoChip()?.let {
            chip = it
            return
        }
        val magic = readRegister(Chips.CHIP_DETECT_MAGIC_REG)
        chip = Chips.byMagic(magic)
            ?: throw EspChipException("Unrecognized chip; magic register = 0x%08x".format(magic))
    }

    private fun securityInfoChip(): Chip? = try {
        val resp = protocol.command(Command.GET_SECURITY_INFO, timeoutMs = Protocol.SHORT_TIMEOUT_MS)
        when {
            resp.data.size >= 20 -> Chips.byChipId(Bytes.readU32(resp.data, 12).toInt())
            resp.data.size >= 12 -> Chips.ESP32S2 // S2 returns a response 8 bytes shorter
            else -> null
        }
    } catch (_: EspException) {
        null
    }

    private fun requireChip(): Chip = chip ?: throw EspChipException("Chip not detected; call connect() first")

    // endregion

    // region registers ----------------------------------------------------------------

    fun readRegister(address: Long): Long {
        val payload = PayloadBuilder().u32(address).build()
        return protocol.command(Command.READ_REG, payload).value
    }

    fun writeRegister(address: Long, value: Long, mask: Long = 0xFFFFFFFFL, delayUs: Int = 0) {
        val payload = PayloadBuilder().u32(address).u32(value).u32(mask).u32(delayUs).build()
        protocol.command(Command.WRITE_REG, payload)
    }

    // endregion

    // region RAM (stub) loading -------------------------------------------------------

    private fun memBegin(size: Long, blocks: Int, blockSize: Int, offset: Long) {
        sequence = 0
        val payload = PayloadBuilder().u32(size).u32(blocks).u32(blockSize).u32(offset).build()
        protocol.command(Command.MEM_BEGIN, payload, timeoutMs = timeoutPerMb(size, MEM_TIMEOUT_PER_MB))
    }

    private fun memBlock(data: ByteArray) {
        val payload = PayloadBuilder().u32(data.size).u32(sequence++).u32(0).u32(0).bytes(data).build()
        protocol.command(Command.MEM_DATA, payload, Command.checksum(data))
    }

    private fun memFinish(entrypoint: Long) {
        val stay = if (entrypoint == 0L) 1 else 0
        val payload = PayloadBuilder().u32(stay).u32(entrypoint).build()
        // ROM may not reply to MEM_END when jumping to the entrypoint; tolerate a timeout.
        try {
            protocol.command(Command.MEM_END, payload)
        } catch (_: EspTimeoutException) {
        }
    }

    /** Upload and start the bundled flasher stub for the detected chip. Returns true if started. */
    fun runStub(): Boolean {
        if (stubRunning) return true
        val target = requireChip()
        val stub = FlasherStub.forChip(target)
            ?: throw EspException("No flasher stub bundled for ${target.name}")
        logger.info("Uploading stub...")
        for (segment in stub.segments) {
            val blocks = ceilDiv(segment.data.size, ESP_RAM_BLOCK)
            memBegin(segment.data.size.toLong(), blocks, ESP_RAM_BLOCK, segment.address)
            var offset = 0
            while (offset < segment.data.size) {
                val end = minOf(offset + ESP_RAM_BLOCK, segment.data.size)
                memBlock(segment.data.copyOfRange(offset, end))
                offset = end
            }
        }
        memFinish(stub.entry)

        reader.startTimer(Protocol.DEFAULT_TIMEOUT_MS)
        val hello = Slip.readFrame(reader)
        if (!hello.contentEquals(OHAI)) {
            throw EspProtocolException("Stub did not report OHAI (got ${Bytes.toHex(hello)})")
        }
        stubRunning = true
        spiAttached = false
        logger.info("Stub running")
        return true
    }

    // endregion

    // region SPI flash setup ----------------------------------------------------------

    private fun ensureSpiAttached() {
        if (spiAttached) return
        val target = requireChip()
        if (target == Chips.ESP8266) {
            // ESP8266 attaches via a zeroed FLASH_BEGIN.
            flashBegin(0, 0, 0, 0, encrypt = false)
        } else {
            val config = readSpiConfig(target)
            spiAttach(config)
        }
        spiAttached = true
    }

    private fun spiAttach(config: Long) {
        val builder = PayloadBuilder().u32(config)
        if (!stubRunning) builder.u32(0) // ROM expects an extra zero word
        protocol.command(Command.SPI_ATTACH, builder.build())
    }

    /** Read the efuse-programmed custom SPI pin config; 0 means default pins (the common case). */
    private fun readSpiConfig(target: Chip): Long = when (target) {
        Chips.ESP32 -> {
            val reg5 = readRegister(target.efuseBase + 5 * 4)
            val reg3 = readRegister(target.efuseBase + 3 * 4)
            val pins = reg5 and 0xfffff
            if (pins == 0L || pins == 0xfffffL) {
                0
            } else {
                fun adj(n: Long) = if (n >= 30) n + 2 else n
                val clk = adj(pins and 0x1f)
                val q = adj((pins shr 5) and 0x1f)
                val d = adj((pins shr 10) and 0x1f)
                val cs = adj((pins shr 15) and 0x1f)
                val hd = adj((reg3 shr 4) and 0x1f)
                (hd shl 24) or (cs shl 18) or (d shl 12) or (q shl 6) or clk
            }
        }
        Chips.ESP32S2, Chips.ESP32S3, Chips.ESP32C3, Chips.ESP32P4 -> {
            val reg1 = readRegister(target.efuseBase + 18 * 4)
            val reg2 = readRegister(target.efuseBase + 19 * 4)
            val pins = ((reg1 shr 16) or ((reg2 and 0xfffff) shl 16)) and 0x3fffffff
            if (pins == 0L || pins == 0xffffffffL) 0 else pins
        }
        else -> 0 // newer chips have fixed SPI pins
    }

    private fun spiSetParams(totalSize: Long) {
        val payload = PayloadBuilder()
            .u32(0) // flash id
            .u32(totalSize) // total size
            .u32(64 * 1024) // block size
            .u32(4 * 1024) // sector size
            .u32(0x100) // page size
            .u32(0xFFFF) // status mask
            .build()
        protocol.command(Command.SPI_SET_PARAMS, payload)
    }

    /**
     * Ensure SPI is attached and SPI_SET_PARAMS has been sent. If [requestedSize] is given it
     * is used directly (matching `esptool --flash-size`), otherwise the size is auto-detected.
     */
    fun initFlashParams(requestedSize: Long? = null) {
        ensureSpiAttached()
        if (flashSizeBytes == 0L) {
            flashSizeBytes = requestedSize ?: detectFlashSize() ?: DEFAULT_FLASH_SIZE
        }
        spiSetParams(flashSizeBytes)
    }

    // endregion

    // region flash ID / size ----------------------------------------------------------

    /** Read the raw 24-bit JEDEC flash ID via the SPI peripheral registers. */
    fun readFlashId(): Long {
        ensureSpiAttached()
        return spiFlashCommand(0x9F, txData = ByteArray(0), txBits = 0, rxBits = 24)
    }

    /** Auto-detect flash size from the JEDEC ID's size byte; null if the byte is unknown. */
    fun detectFlashSize(): Long? {
        val id = readFlashId()
        val sizeId = (id shr 16).toInt() and 0xFF
        return FLASH_SIZE_BY_ID[sizeId]
    }

    private fun spiFlashCommand(cmd: Int, txData: ByteArray, txBits: Int, rxBits: Int): Long {
        val regs = requireChip().spi
        val oldUsr = readRegister(regs.usr)
        val oldUsr2 = readRegister(regs.usr2)

        if (txBits > 0) writeRegister(regs.mosiDlen, (txBits - 1).toLong())
        if (rxBits > 0) writeRegister(regs.misoDlen, (rxBits - 1).toLong())

        val usrReg2 = (7L shl 28) or cmd.toLong()
        var usrReg = 1L shl 31 // SPI_USR_CMD
        if (rxBits > 0) usrReg = usrReg or (1L shl 28) // SPI_USR_MISO
        if (txBits > 0) usrReg = usrReg or (1L shl 27) // SPI_USR_MOSI
        writeRegister(regs.usr, usrReg)
        writeRegister(regs.usr2, usrReg2)

        if (txBits == 0) {
            writeRegister(regs.w0, 0)
        } else {
            val words = (txBits + 31) / 32
            for (i in 0 until words) {
                val word = Bytes.readU32(txData.copyOf(words * 4), i * 4)
                writeRegister(regs.w0 + i * 4, word)
            }
        }

        writeRegister(regs.cmd, 1L shl 18) // SPI_CMD_USR
        var trials = 10
        while (trials-- > 0) {
            if ((readRegister(regs.cmd) and (1L shl 18)) == 0L) break
        }
        if (trials <= 0) throw EspTimeoutException("SPI flash command timed out")

        val result = readRegister(regs.w0)
        writeRegister(regs.usr, oldUsr)
        writeRegister(regs.usr2, oldUsr2)
        return result
    }

    // endregion

    // region flash write primitives ---------------------------------------------------

    /** FLASH block size: large for the stub, small for the ROM loader. */
    fun flashWriteSize(): Int = if (stubRunning) STUB_FLASH_WRITE_SIZE else ROM_FLASH_WRITE_SIZE

    private fun encryptionField(): Boolean = requireChip().encryptionInBeginFlashCmd && !stubRunning

    fun flashBegin(offset: Long, eraseSize: Long, blockSize: Int, blocks: Int, encrypt: Boolean = encryptionField()) {
        sequence = 0
        val builder = PayloadBuilder().u32(eraseSize).u32(blocks).u32(blockSize).u32(offset)
        if (encrypt) builder.u32(0)
        protocol.command(Command.FLASH_BEGIN, builder.build(), timeoutMs = timeoutPerMb(eraseSize, ERASE_TIMEOUT_PER_MB))
    }

    fun flashBlock(data: ByteArray) {
        val payload = PayloadBuilder().u32(data.size).u32(sequence++).u32(0).u32(0).bytes(data).build()
        protocol.command(Command.FLASH_DATA, payload, Command.checksum(data))
    }

    fun flashDeflBegin(writeSize: Long, blocks: Int, blockSize: Int, offset: Long, eraseSize: Long, encrypt: Boolean = encryptionField()) {
        sequence = 0
        val builder = PayloadBuilder().u32(writeSize).u32(blocks).u32(blockSize).u32(offset)
        if (encrypt) builder.u32(0)
        protocol.command(Command.FLASH_DEFL_BEGIN, builder.build(), timeoutMs = timeoutPerMb(eraseSize, ERASE_TIMEOUT_PER_MB))
    }

    fun flashDeflBlock(data: ByteArray) {
        val payload = PayloadBuilder().u32(data.size).u32(sequence++).u32(0).u32(0).bytes(data).build()
        protocol.command(Command.FLASH_DEFL_DATA, payload, Command.checksum(data))
    }

    /** End a (plain) flash write. Only the stub needs/answers this; ROM reboots via hard reset. */
    fun flashFinish(reboot: Boolean = false) {
        if (!stubRunning) return
        val payload = PayloadBuilder().u32(if (reboot) 0 else 1).build()
        protocol.command(Command.FLASH_END, payload)
    }

    fun flashDeflFinish(reboot: Boolean = false) {
        if (!stubRunning) return
        val payload = PayloadBuilder().u32(if (reboot) 0 else 1).build()
        protocol.command(Command.FLASH_DEFL_END, payload)
    }

    // endregion

    // region erase / md5 / read -------------------------------------------------------

    fun eraseFlash() {
        if (!stubRunning) throw EspException("erase_flash requires the flasher stub (run without --no-stub)")
        initFlashParams()
        protocol.command(Command.ERASE_FLASH, timeoutMs = timeoutPerMb(flashSizeBytes, ERASE_TIMEOUT_PER_MB))
    }

    fun eraseRegion(offset: Long, size: Long) {
        require(offset % FLASH_SECTOR_SIZE == 0L && size % FLASH_SECTOR_SIZE == 0L) {
            "erase_region offset and size must be multiples of 0x1000"
        }
        if (!stubRunning) throw EspException("erase_region requires the flasher stub")
        initFlashParams()
        val payload = PayloadBuilder().u32(offset).u32(size).build()
        protocol.command(Command.ERASE_REGION, payload, timeoutMs = timeoutPerMb(size, ERASE_TIMEOUT_PER_MB))
    }

    /** Return the chip-computed MD5 of a flash region as a lowercase hex string. */
    fun flashMd5(address: Long, size: Long): String {
        initFlashParams()
        val payload = PayloadBuilder().u32(address).u32(size).u32(0).u32(0).build()
        val resp = protocol.command(Command.SPI_FLASH_MD5, payload, timeoutMs = timeoutPerMb(size, MD5_TIMEOUT_PER_MB))
        return if (resp.data.size >= 32) {
            String(resp.data.copyOfRange(0, 32), Charsets.US_ASCII) // ROM returns ASCII hex
        } else {
            Bytes.toHex(resp.data.copyOfRange(0, 16)) // stub returns 16 raw bytes
        }
    }

    /** Read [length] bytes of flash starting at [address] (stub fast path). */
    fun readFlash(address: Long, length: Int): ByteArray {
        if (!stubRunning) throw EspException("read_flash currently requires the flasher stub")
        initFlashParams()
        val packetSize = minOf(length, STUB_READ_PACKET)
        reader.startTimer(Protocol.DEFAULT_TIMEOUT_MS)
        protocol.write(
            Command.READ_FLASH_STUB,
            PayloadBuilder().u32(address).u32(length.toLong()).u32(packetSize.toLong()).u32(1).build(),
        )
        protocol.readResponse(Command.READ_FLASH_STUB)

        val out = ByteArray(length)
        var received = 0
        while (received < length) {
            reader.startTimer(Protocol.DEFAULT_TIMEOUT_MS)
            val packet = Slip.readFrame(reader)
            packet.copyInto(out, received)
            received += packet.size
            logger.progress("Reading", received.toLong(), length.toLong())
            // Acknowledge total bytes received so far.
            Slip.writeFrame(transport, Bytes.u32(received))
        }
        // Final frame is the device-side MD5 digest.
        reader.startTimer(Protocol.DEFAULT_TIMEOUT_MS)
        Slip.readFrame(reader)
        return out
    }

    // endregion

    // region misc ---------------------------------------------------------------------

    /** Read the factory MAC address (6 bytes) from efuse. */
    fun readMac(): ByteArray {
        val target = requireChip()
        if (target == Chips.ESP8266) throw EspChipException("read_mac is not supported on ESP8266")
        val p1 = readRegister(target.efuseBase + target.macEfuseOffset)
        val p2 = readRegister(target.efuseBase + target.macEfuseOffset + 4)
        return byteArrayOf(
            ((p2 shr 8) and 0xff).toByte(),
            (p2 and 0xff).toByte(),
            ((p1 shr 24) and 0xff).toByte(),
            ((p1 shr 16) and 0xff).toByte(),
            ((p1 shr 8) and 0xff).toByte(),
            (p1 and 0xff).toByte(),
        )
    }

    /** Switch the line speed: tell the chip, then reconfigure the host transport. */
    fun changeBaud(newBaud: Int) {
        val oldBaud = if (stubRunning) transport.baudRate else 0
        val payload = PayloadBuilder().u32(newBaud).u32(oldBaud).build()
        protocol.command(Command.CHANGE_BAUDRATE, payload)
        if (stubRunning) Thread.sleep(25)
        transport.baudRate = newBaud
        reader.flush()
        logger.info("Changed baud rate to $newBaud")
    }

    /** Reboot the chip into the application using [after]. */
    fun hardReset(after: ResetStrategy) {
        stubRunning = false
        spiAttached = false
        after.reset(transport)
    }

    // endregion

    // region memory / status / security / RAM ----------------------------------------

    /** Read a single 32-bit word from the chip's address space (alias of [readRegister]). */
    fun readMem(address: Long): Long = readRegister(address)

    /** Write a single 32-bit word into the chip's address space (alias of [writeRegister]). */
    fun writeMem(address: Long, value: Long) = writeRegister(address, value)

    /** Dump [size] bytes from a memory-mapped region by reading consecutive words. */
    fun dumpMem(address: Long, size: Int): ByteArray {
        val padded = ByteArray(((size + 3) / 4) * 4)
        var off = 0
        while (off < padded.size) {
            Bytes.u32(readRegister(address + off)).copyInto(padded, off)
            off += 4
            logger.progress("Dumping", off.toLong(), padded.size.toLong())
        }
        return padded.copyOf(size)
    }

    /** Read up to 3 bytes of the SPI flash status register (RDSR/RDSR2/RDSR3). */
    fun readFlashStatus(numBytes: Int = 2): Long {
        ensureSpiAttached()
        val cmds = intArrayOf(0x05, 0x35, 0x15)
        var status = 0L
        var shift = 0
        for (i in 0 until numBytes.coerceIn(1, 3)) {
            status = status or ((spiFlashCommand(cmds[i], ByteArray(0), 0, 8) and 0xFF) shl shift)
            shift += 8
        }
        return status
    }

    /**
     * Write the SPI flash status register (WREN/WEVSR + WRSR per byte). Controls flash protection
     * bits — use with care. [nonVolatile] selects WREN (persistent) over WEVSR (volatile).
     */
    fun writeFlashStatus(value: Long, numBytes: Int = 2, nonVolatile: Boolean = false) {
        ensureSpiAttached()
        val wrsr = intArrayOf(0x01, 0x31, 0x11)
        val enable = if (nonVolatile) 0x06 else 0x50 // WREN / WEVSR
        for (i in 0 until numBytes.coerceIn(1, 3)) {
            spiFlashCommand(enable, ByteArray(0), 0, 0)
            spiFlashCommand(wrsr[i], Bytes.u32((value shr (i * 8)) and 0xFF), 8, 0)
            var trials = 10
            while (trials-- > 0 && (spiFlashCommand(0x05, ByteArray(0), 0, 8) and 0x01) != 0L) { /* WIP */ }
        }
        spiFlashCommand(0x04, ByteArray(0), 0, 0) // WRDI
    }

    /** Query GET_SECURITY_INFO and parse it; null if the chip/ROM does not support the command. */
    fun securityInfo(): SecurityInfo? = try {
        val resp = protocol.command(Command.GET_SECURITY_INFO, timeoutMs = Protocol.SHORT_TIMEOUT_MS)
        if (resp.data.size >= 12) SecurityInfo.parse(resp.data) else null
    } catch (_: EspException) {
        null
    }

    /**
     * Load an ESP image's segments into RAM and jump to its entry point, bypassing flash
     * (esptool `load_ram`). Requires the ROM loader (connect with the stub disabled).
     */
    fun loadRam(image: io.github.ajsb85.esptoolkt.image.EspImage) {
        if (stubRunning) throw EspException("load_ram requires the ROM loader; connect with --no-stub")
        for (segment in image.segments) {
            val blocks = ceilDiv(segment.data.size, ESP_RAM_BLOCK)
            memBegin(segment.data.size.toLong(), blocks, ESP_RAM_BLOCK, segment.address)
            var off = 0
            while (off < segment.data.size) {
                val end = minOf(off + ESP_RAM_BLOCK, segment.data.size)
                memBlock(segment.data.copyOfRange(off, end))
                off = end
            }
        }
        memFinish(image.entry)
    }

    // endregion

    private fun timeoutPerMb(sizeBytes: Long, perMb: Int): Int {
        val t = (perMb.toLong() * sizeBytes / 1_000_000L).toInt()
        return maxOf(t, Protocol.DEFAULT_TIMEOUT_MS)
    }

    companion object {
        const val ESP_RAM_BLOCK = 0x1800
        const val STUB_FLASH_WRITE_SIZE = 0x4000
        const val ROM_FLASH_WRITE_SIZE = 0x400
        const val STUB_READ_PACKET = 0x1000
        const val FLASH_SECTOR_SIZE = 0x1000L
        const val DEFAULT_FLASH_SIZE = 2L * 1024 * 1024

        const val SYNC_TIMEOUT_MS = 100
        const val MEM_TIMEOUT_PER_MB = 2000
        const val ERASE_TIMEOUT_PER_MB = 10000
        const val MD5_TIMEOUT_PER_MB = 8000

        private val OHAI = "OHAI".toByteArray(Charsets.US_ASCII)

        private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

        /** JEDEC size-byte → capacity, from esp-serial-flasher's `size_mapping`. */
        private val FLASH_SIZE_BY_ID: Map<Int, Long> = mapOf(
            0x12 to 256L * 1024, 0x13 to 512L * 1024, 0x14 to 1L * 1024 * 1024,
            0x15 to 2L * 1024 * 1024, 0x16 to 4L * 1024 * 1024, 0x17 to 8L * 1024 * 1024,
            0x18 to 16L * 1024 * 1024, 0x19 to 32L * 1024 * 1024, 0x1A to 64L * 1024 * 1024,
            0x1B to 128L * 1024 * 1024, 0x1C to 256L * 1024 * 1024,
            0x20 to 64L * 1024 * 1024, 0x21 to 128L * 1024 * 1024, 0x22 to 256L * 1024 * 1024,
            0x32 to 256L * 1024, 0x33 to 512L * 1024, 0x34 to 1L * 1024 * 1024,
            0x35 to 2L * 1024 * 1024, 0x36 to 4L * 1024 * 1024, 0x37 to 8L * 1024 * 1024,
            0x38 to 16L * 1024 * 1024, 0x39 to 32L * 1024 * 1024, 0x3A to 64L * 1024 * 1024,
        )
    }
}
