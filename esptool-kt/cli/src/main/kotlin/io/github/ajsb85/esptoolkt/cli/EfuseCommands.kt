package io.github.ajsb85.esptoolkt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import io.github.ajsb85.esptoolkt.efuse.BurnConfirmation
import io.github.ajsb85.esptoolkt.efuse.EfuseController

class EfuseSummary(private val g: GlobalOptions) : CliktCommand(name = "efuse_summary") {
    override fun help(context: Context) = "Read and print the eFuse security/config summary (ESP32-S2)."
    override fun run() = g.withLoader(loadStub = false, switchBaud = false) {
        println(EfuseController(it).summary())
    }
}

class BurnEfuse(private val g: GlobalOptions) : CliktCommand(name = "burn_efuse") {
    override fun help(context: Context) = buildString {
        append("Burn a BLOCK0 eFuse: <field> <value>. ")
        append("DRY RUN unless '--confirm ${BurnConfirmation.PHRASE}' is given. ")
        append("eFuse burns are PERMANENT and IRREVERSIBLE.")
    }

    private val confirm: String? by option(
        "--confirm",
        help = "Pass exactly '${BurnConfirmation.PHRASE}' to actually burn (otherwise dry run).",
    )
    private val field: String by argument(name = "field")
    private val value: String by argument(name = "value")

    override fun run() = g.withLoader(loadStub = false, switchBaud = false, resetAfter = false) { loader ->
        val controller = EfuseController(loader)
        val plan = controller.plan(field, parseOffset(value))
        println(plan.describe())

        if (confirm == null) {
            println()
            println("DRY RUN — nothing was burned. To apply, re-run with: --confirm ${BurnConfirmation.PHRASE}")
            return@withLoader
        }
        if (plan.blocked != null) {
            println()
            println("Refused: ${plan.blocked}")
            return@withLoader
        }
        if (confirm != BurnConfirmation.PHRASE) {
            println()
            println("Refused: --confirm must be exactly '${BurnConfirmation.PHRASE}' (got '$confirm'). Nothing was burned.")
            return@withLoader
        }
        println()
        println(">>> BURNING ${plan.field.name} — this is permanent <<<")
        val result = controller.burn(plan, BurnConfirmation.of(confirm!!))
        println("Burned ${result.field} -> 0x%x (verified=${result.verified})".format(result.finalValue))
        loader.hardReset(afterStrategy(g.after))
    }
}
