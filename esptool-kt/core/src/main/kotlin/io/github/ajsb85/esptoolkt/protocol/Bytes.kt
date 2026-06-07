package io.github.ajsb85.esptoolkt.protocol

import java.io.ByteArrayOutputStream

/** Little-endian byte helpers used throughout the protocol layer (ESP wire format is LE). */
internal object Bytes {
    fun u32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    fun u32(value: Int): ByteArray = u32(value.toLong() and 0xFFFFFFFFL)

    fun u16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    fun readU32(b: ByteArray, offset: Int): Long = (b[offset].toLong() and 0xFF) or
        ((b[offset + 1].toLong() and 0xFF) shl 8) or
        ((b[offset + 2].toLong() and 0xFF) shl 16) or
        ((b[offset + 3].toLong() and 0xFF) shl 24)

    fun readU16(b: ByteArray, offset: Int): Int = (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    fun toHex(b: ByteArray): String = buildString(b.size * 2) {
        for (x in b) append("%02x".format(x.toInt() and 0xFF))
    }
}

/** Builds a command payload by appending little-endian fields, mirroring the packed C structs. */
internal class PayloadBuilder {
    private val out = ByteArrayOutputStream()

    fun u32(value: Long): PayloadBuilder {
        out.write(Bytes.u32(value))
        return this
    }
    fun u32(value: Int): PayloadBuilder {
        out.write(Bytes.u32(value))
        return this
    }
    fun bytes(data: ByteArray): PayloadBuilder {
        out.write(data)
        return this
    }

    fun build(): ByteArray = out.toByteArray()
}
