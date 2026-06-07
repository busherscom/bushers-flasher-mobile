package io.github.ajsb85.esptoolkt.protocol

/**
 * ESP bootloader command opcodes. Values mirror `command_t` in esp-serial-flasher's
 * `protocol.h` and esptool.py's `ESPLoader`.
 */
enum class Command(val opcode: Int) {
    FLASH_BEGIN(0x02),
    FLASH_DATA(0x03),
    FLASH_END(0x04),
    MEM_BEGIN(0x05),
    MEM_END(0x06),
    MEM_DATA(0x07),
    SYNC(0x08),
    WRITE_REG(0x09),
    READ_REG(0x0A),
    SPI_SET_PARAMS(0x0B),
    SPI_ATTACH(0x0D),
    READ_FLASH_ROM(0x0E),
    CHANGE_BAUDRATE(0x0F),
    FLASH_DEFL_BEGIN(0x10),
    FLASH_DEFL_DATA(0x11),
    FLASH_DEFL_END(0x12),
    SPI_FLASH_MD5(0x13),
    GET_SECURITY_INFO(0x14),
    ERASE_FLASH(0xD0),
    ERASE_REGION(0xD1),
    READ_FLASH_STUB(0xD2),
    ;

    companion object {
        const val DIR_REQUEST = 0x00
        const val DIR_RESPONSE = 0x01

        /** Data checksum: seed 0xEF XOR-folded over the payload bytes (esptool `ESP_CHECKSUM_MAGIC`). */
        fun checksum(data: ByteArray, from: Int = 0, length: Int = data.size - from): Int {
            var cs = 0xEF
            for (i in from until from + length) cs = cs xor (data[i].toInt() and 0xFF)
            return cs and 0xFF
        }
    }
}

/**
 * A parsed bootloader response packet.
 *
 * Wire layout (after SLIP decode): `dir(1)=1 | op(1) | size(2) | value(4) | data... | status(2)`,
 * where the trailing two status bytes are `failed` and `errorCode` (`response_status_t`).
 */
class Response(
    val command: Int,
    val value: Long,
    val data: ByteArray,
    val failed: Boolean,
    val errorCode: Int,
) {
    companion object {
        const val STATUS_BYTES = 2

        fun parse(frame: ByteArray): Response {
            require(frame.size >= 8 + STATUS_BYTES) { "Response frame too short: ${frame.size} bytes" }
            val command = frame[1].toInt() and 0xFF
            val value = Bytes.readU32(frame, 4)
            val statusOffset = frame.size - STATUS_BYTES
            val failed = (frame[statusOffset].toInt() and 0xFF) != 0
            val errorCode = frame[statusOffset + 1].toInt() and 0xFF
            val data = frame.copyOfRange(8, statusOffset)
            return Response(command, value, data, failed, errorCode)
        }
    }
}
