package io.github.ajsb85.esptoolkt.stub

import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.targets.Chip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** A loadable RAM segment of a flasher stub. */
class StubSegment(val address: Long, val data: ByteArray)

/**
 * A decoded flasher stub: small RAM programs uploaded via MEM_* and started at [entry],
 * after which the chip replies `OHAI` and speaks the same protocol but faster (larger
 * flash blocks, compression, host-side MD5 over read data, etc.).
 */
class FlasherStub(
    val entry: Long,
    val segments: List<StubSegment>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class StubJson(
            val entry: Long,
            val text: String,
            @SerialName("text_start") val textStart: Long,
            val data: String? = null,
            @SerialName("data_start") val dataStart: Long = 0,
        )

        /** Load and decode the bundled stub for [chip], or null if none is available. */
        fun forChip(chip: Chip): FlasherStub? {
            val resource = chip.stubResource ?: return null
            val text = FlasherStub::class.java.getResourceAsStream("/stubs/$resource")
                ?.bufferedReader()?.use { it.readText() }
                ?: throw EspException("Missing bundled flasher stub: /stubs/$resource")
            val parsed = json.decodeFromString(StubJson.serializer(), text)
            val decoder = Base64.getDecoder()
            val segments = buildList {
                add(StubSegment(parsed.textStart, decoder.decode(parsed.text.trim())))
                parsed.data?.let { add(StubSegment(parsed.dataStart, decoder.decode(it.trim()))) }
            }
            return FlasherStub(parsed.entry, segments)
        }
    }
}
