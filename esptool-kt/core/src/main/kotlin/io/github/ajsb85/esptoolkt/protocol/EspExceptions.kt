package io.github.ajsb85.esptoolkt.protocol

/** Base type for all esptool-kt protocol failures. */
open class EspException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A timer set by the protocol layer expired before the expected bytes arrived. */
class EspTimeoutException(message: String = "Timed out waiting for serial data") : EspException(message)

/** A SLIP frame or response packet was malformed or had an unexpected shape. */
class EspProtocolException(message: String) : EspException(message)

/**
 * The chip's ROM/stub loader returned a non-zero status for a command.
 *
 * Mirrors `error_code_t` in esp-serial-flasher's `protocol.h`.
 */
class EspCommandException(
    val command: Int,
    val errorCode: Int,
) : EspException(buildMessage(command, errorCode)) {
    companion object {
        private fun buildMessage(command: Int, error: Int): String {
            val name = ERROR_NAMES[error] ?: "UNKNOWN_ERROR"
            return "Command 0x%02x failed: %s (0x%02x)".format(command, name, error)
        }

        /** ROM and stub loader error codes. */
        val ERROR_NAMES: Map<Int, String> = mapOf(
            0x05 to "INVALID_COMMAND",
            0x06 to "COMMAND_FAILED",
            0x07 to "INVALID_CRC",
            0x08 to "FLASH_WRITE_ERR",
            0x09 to "FLASH_READ_ERR",
            0x0a to "READ_LENGTH_ERR",
            0x0b to "DEFLATE_ERROR",
            0xc0 to "STUB_BAD_DATA_LEN",
            0xc1 to "STUB_BAD_DATA_CHECKSUM",
            0xc2 to "STUB_BAD_BLOCKSIZE",
            0xc3 to "STUB_INVALID_COMMAND",
            0xc4 to "STUB_FAILED_SPI_OP",
            0xc5 to "STUB_FAILED_SPI_UNLOCK",
            0xc6 to "STUB_NOT_IN_FLASH_MODE",
            0xc7 to "STUB_INFLATE_ERROR",
            0xc8 to "STUB_NOT_ENOUGH_DATA",
            0xc9 to "STUB_TOO_MUCH_DATA",
            0xff to "STUB_CMD_NOT_IMPLEMENTED",
        )
    }
}

/** Raised when the connected chip could not be identified or is unsupported. */
class EspChipException(message: String) : EspException(message)

/** Raised when a flash MD5 verification does not match the data written. */
class EspMd5MismatchException(message: String) : EspException(message)
