package io.github.ajsb85.esptoolkt.efuse

import io.github.ajsb85.esptoolkt.EspLoader
import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.targets.Chips

/**
 * Explicit, tamper-resistant acknowledgement required to actually burn eFuses.
 *
 * eFuse programming is **permanent and irreversible** — a burned bit can never be cleared, and a
 * wrong burn can brick the chip or lock you out (disable JTAG/USB/download, enable secure boot).
 * A burn cannot be performed by accident: the caller must construct this token with the exact
 * acknowledgement phrase, which the CLI surfaces as `--confirm`.
 */
class BurnConfirmation private constructor() {
    companion object {
        const val PHRASE = "BURN"

        /** Build a confirmation, throwing unless [acknowledgement] is exactly [PHRASE]. */
        fun of(acknowledgement: String): BurnConfirmation {
            if (acknowledgement != PHRASE) {
                throw EspException("eFuse burn not confirmed; acknowledgement must be exactly \"$PHRASE\"")
            }
            return BurnConfirmation()
        }
    }
}

/**
 * A computed, side-effect-free description of a prospective burn. Produced by [EfuseController.plan]
 * and printed verbatim by the CLI's dry run. If [blocked] is non-null the burn is refused.
 */
class BurnPlan(
    val field: EfuseField,
    val currentValue: Long,
    val requestedValue: Long,
    val newBits: Long,
    internal val targetWords: LongArray,
    val warnings: List<String>,
    val blocked: String?,
) {
    fun describe(): String = buildString {
        appendLine("eFuse:     ${field.name}  (${field.description})")
        appendLine("Current:   0x%x".format(currentValue))
        appendLine("Requested: 0x%x".format(requestedValue))
        appendLine("New bits:  0x%x".format(newBits))
        warnings.forEach { appendLine("WARNING:   $it") }
        if (blocked != null) append("BLOCKED:   $blocked") else append("This burn is PERMANENT and IRREVERSIBLE.")
    }
}

/** Outcome of a real burn. */
class BurnResult(val field: String, val finalValue: Long, val verified: Boolean)

/**
 * Read and (with explicit confirmation) burn ESP32-S2 BLOCK0 eFuses. Only BLOCK0 config/security
 * fields are supported; key/data blocks (which need Reed-Solomon coding) are intentionally not
 * burnable here to avoid corrupting them.
 *
 * The burn register sequence mirrors esptool's `esp32s2/fields.py` exactly.
 */
class EfuseController(private val loader: EspLoader) {

    init {
        if (loader.chip != Chips.ESP32S2) {
            throw EspException("eFuse support currently targets ESP32-S2 (detected ${loader.chip?.name})")
        }
    }

    /** Read BLOCK0's six words from the read registers. */
    fun readBlock0(): LongArray = LongArray(EfuseDefsS2.BLOCK0_WORDS) { loader.readMem(EfuseDefsS2.BLOCK0_RD + it * 4) }

    private fun valueOf(field: EfuseField, words: LongArray): Long = (words[field.word] ushr field.pos) and field.mask

    private fun isWriteProtected(field: EfuseField, words: LongArray): Boolean {
        val bit = field.wrDisBit ?: return false
        val wrDis = words[0] // WR_DIS is word 0
        return (wrDis ushr bit) and 1L == 1L
    }

    /** Human-readable summary of BLOCK0 security/config fields. */
    fun summary(): String {
        val words = readBlock0()
        val mac = runCatching { loader.readMac() }.getOrNull()
        return buildString {
            appendLine("=== eFuse summary (${loader.chip?.name}) ===")
            mac?.let { appendLine("MAC:                          " + it.joinToString(":") { b -> "%02x".format(b) }) }
            for (f in EfuseDefsS2.FIELDS) {
                if (f.name == "WR_DIS") continue
                val v = valueOf(f, words)
                val wp = if (isWriteProtected(f, words)) " [write-protected]" else ""
                appendLine("%-28s = 0x%x%s".format(f.name, v, wp))
            }
            append("WR_DIS                       = 0x%x".format(words[0]))
        }
    }

    /** Compute (but do not perform) a burn. Always safe to call. */
    fun plan(fieldName: String, value: Long): BurnPlan {
        val field = EfuseDefsS2.field(fieldName)
            ?: throw EspException("Unknown eFuse field: $fieldName")
        val words = readBlock0()
        val current = valueOf(field, words)
        val warnings = mutableListOf<String>()
        var blocked: String? = null

        if (field.name == "WR_DIS") {
            blocked = "Burning WR_DIS directly is not supported (it would lock other eFuses)."
        }
        if (value and field.mask.inv() != 0L) {
            blocked = blocked ?: "Value 0x%x does not fit in ${field.len}-bit field ${field.name}".format(value)
        }
        if (isWriteProtected(field, words)) {
            blocked = blocked ?: "${field.name} is write-protected (WR_DIS bit ${field.wrDisBit} is set)."
        }
        // eFuses are one-way: a bit that is already 1 can never go back to 0.
        val bitsToClear = current and value.inv() and field.mask
        if (bitsToClear != 0L) {
            blocked = blocked ?: "Cannot clear already-set bits (0x%x); eFuses are one-way.".format(bitsToClear)
        }

        val newBits = value and current.inv() and field.mask
        if (newBits == 0L && blocked == null) warnings += "${field.name} already has this value; nothing to burn."
        if (field.category == "security") warnings += "This is a SECURITY fuse; burning it can permanently lock the device."

        val targetWords = LongArray(EfuseDefsS2.BLOCK0_WORDS)
        targetWords[field.word] = (newBits shl field.pos) and 0xFFFFFFFFL
        return BurnPlan(field, current, value, newBits, targetWords, warnings, blocked)
    }

    /**
     * Perform a burn previously produced by [plan]. Requires a [BurnConfirmation]. Refuses any
     * blocked plan. After programming it triggers a read cycle and verifies the value.
     */
    fun burn(plan: BurnPlan, confirmation: BurnConfirmation): BurnResult {
        @Suppress("UNUSED_EXPRESSION")
        confirmation // presence is the gate
        plan.blocked?.let { throw EspException("Refusing to burn: $it") }
        if (plan.newBits == 0L) return BurnResult(plan.field.name, plan.currentValue, verified = true)

        setEfuseTiming()
        clearPgmRegisters()
        waitIdle()

        // Write the to-burn words into the programming-data registers, then issue PGM for BLOCK0.
        for (i in 0 until EfuseDefsS2.BLOCK0_WORDS) {
            loader.writeMem(EfuseDefsS2.PGM_DATA0 + i * 4, plan.targetWords[i])
        }
        program(block = 0)
        clearPgmRegisters()
        triggerRead()

        val words = readBlock0()
        val finalValue = valueOf(plan.field, words)
        val verified = (finalValue and plan.requestedValue) == plan.requestedValue
        return BurnResult(plan.field.name, finalValue, verified)
    }

    // --- low-level controller sequence (mirrors esptool esp32s2/fields.py) ---

    private fun program(block: Int) {
        waitIdle()
        loader.writeMem(EfuseDefsS2.CONF, EfuseDefsS2.WRITE_OP_CODE)
        loader.writeMem(EfuseDefsS2.CMD, EfuseDefsS2.PGM_CMD or (block.toLong() shl 2))
        waitIdle()
    }

    private fun triggerRead() {
        waitIdle()
        loader.writeMem(EfuseDefsS2.CONF, EfuseDefsS2.READ_OP_CODE)
        loader.writeMem(EfuseDefsS2.CMD, EfuseDefsS2.READ_CMD)
        Thread.sleep(2)
        waitIdle()
    }

    private fun clearPgmRegisters() {
        waitIdle()
        for (i in 0 until 8) loader.writeMem(EfuseDefsS2.PGM_DATA0 + i * 4, 0)
    }

    private fun waitIdle() {
        val mask = EfuseDefsS2.PGM_CMD or EfuseDefsS2.READ_CMD
        repeat(1000) {
            if (loader.readMem(EfuseDefsS2.CMD) and mask == 0L) return
        }
        throw EspException("Timed out waiting for eFuse controller")
    }

    private fun setEfuseTiming() {
        updateReg(EfuseDefsS2.WR_TIM_CONF1, 0x000000FFL, EfuseDefsS2.TSUP_A)
        updateReg(EfuseDefsS2.WR_TIM_CONF1, 0x00FFFF00L, EfuseDefsS2.PWR_ON_NUM)
        updateReg(EfuseDefsS2.WR_TIM_CONF0, 0xFFFF0000L, EfuseDefsS2.TPGM)
        updateReg(EfuseDefsS2.WR_TIM_CONF0, 0x0000FF00L, EfuseDefsS2.TPGM_INACTIVE)
        updateReg(EfuseDefsS2.WR_TIM_CONF0, 0x000000FFL, EfuseDefsS2.THP_A)
        updateReg(EfuseDefsS2.DAC_CONF, 0x000000FFL, EfuseDefsS2.DAC_CLK_DIV)
        updateReg(EfuseDefsS2.WR_TIM_CONF2, 0x0000FFFFL, EfuseDefsS2.PWR_OFF_NUM)
    }

    private fun updateReg(addr: Long, mask: Long, value: Long) {
        val shift = java.lang.Long.numberOfTrailingZeros(mask)
        val old = loader.readMem(addr)
        loader.writeMem(addr, (old and mask.inv()) or ((value shl shift) and mask))
    }
}
