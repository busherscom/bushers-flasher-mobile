package io.github.ajsb85.esptoolkt.protocol

import io.github.ajsb85.esptoolkt.transport.SerialTransport

/**
 * Request/response layer over SLIP: builds the 8-byte command header, sends the frame,
 * and reads back the matching response (filtering by direction + opcode, as the reference
 * `uart_check_response` does), raising [EspCommandException] on a non-zero status.
 */
class Protocol(
    private val transport: SerialTransport,
    val reader: SerialReader,
) {
    /** Optional hex trace hook for debugging the wire (`--trace`). */
    var trace: ((String) -> Unit)? = null

    private fun buildFrame(op: Command, payload: ByteArray, checksum: Int): ByteArray {
        val frame = ByteArray(8 + payload.size)
        frame[0] = Command.DIR_REQUEST.toByte()
        frame[1] = op.opcode.toByte()
        Bytes.u16(payload.size).copyInto(frame, 2)
        Bytes.u32(checksum).copyInto(frame, 4)
        payload.copyInto(frame, 8)
        return frame
    }

    /** Send a command frame without waiting for a response. */
    fun write(op: Command, payload: ByteArray = ByteArray(0), checksum: Int = 0) {
        val frame = buildFrame(op, payload, checksum)
        trace?.invoke("TX ${op.name} ${Bytes.toHex(frame)}")
        Slip.writeFrame(transport, frame)
    }

    /** Read the next response whose direction is RESPONSE and opcode matches [op]. */
    fun readResponse(op: Command): Response {
        while (true) {
            val frame = Slip.readFrame(reader)
            if (frame.size < 8 + Response.STATUS_BYTES) continue
            if ((frame[0].toInt() and 0xFF) != Command.DIR_RESPONSE) continue
            if ((frame[1].toInt() and 0xFF) != op.opcode) continue
            trace?.invoke("RX ${op.name} ${Bytes.toHex(frame)}")
            return Response.parse(frame)
        }
    }

    /**
     * Send [op] and return its response, throwing on timeout or a failed status.
     * [timeoutMs] bounds the whole exchange.
     */
    fun command(
        op: Command,
        payload: ByteArray = ByteArray(0),
        checksum: Int = 0,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Response {
        reader.startTimer(timeoutMs)
        write(op, payload, checksum)
        val resp = readResponse(op)
        if (resp.failed) throw EspCommandException(op.opcode, resp.errorCode)
        return resp
    }

    companion object {
        const val SHORT_TIMEOUT_MS = 100
        const val DEFAULT_TIMEOUT_MS = 3000
    }
}
