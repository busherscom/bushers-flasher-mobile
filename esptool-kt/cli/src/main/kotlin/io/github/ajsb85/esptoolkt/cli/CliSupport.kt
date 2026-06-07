package io.github.ajsb85.esptoolkt.cli

import io.github.ajsb85.esptoolkt.EspLoader
import io.github.ajsb85.esptoolkt.EspLogger
import io.github.ajsb85.esptoolkt.jvm.JSerialCommTransport
import io.github.ajsb85.esptoolkt.transport.ClassicReset
import io.github.ajsb85.esptoolkt.transport.CustomReset
import io.github.ajsb85.esptoolkt.transport.HardReset
import io.github.ajsb85.esptoolkt.transport.ResetStrategy
import io.github.ajsb85.esptoolkt.transport.UsbJtagReset

const val INITIAL_BAUD = 115200

/** Maps the `--before` option to a reset-into-bootloader strategy. */
fun beforeStrategy(name: String): ResetStrategy = when (name) {
    "default_reset", "default-reset" -> ClassicReset()
    "usb_reset", "usb-reset" -> UsbJtagReset()
    "no_reset", "no-reset" -> ResetStrategy { }
    else -> CustomReset(name)
}

/** Maps the `--after` option to a post-operation reset strategy. */
fun afterStrategy(name: String): ResetStrategy = when (name) {
    "hard_reset", "hard-reset" -> HardReset()
    "no_reset", "no-reset", "soft_reset", "soft-reset" -> ResetStrategy { }
    else -> CustomReset(name)
}

/** Logger that prints status to stdout and live progress bars to stderr. */
class ConsoleLogger(private val verbose: Boolean) : EspLogger {
    private var lastLabel = ""
    override fun info(message: String) = println(message)
    override fun detail(message: String) {
        if (verbose) System.err.println(message)
    }

    override fun progress(label: String, done: Long, total: Long) {
        if (total <= 0) return
        val pct = (done * 100 / total).toInt()
        lastLabel = label
        System.err.print("\r  $label: $pct%% (%d/%d bytes)".format(done, total))
        if (done >= total) System.err.println()
    }
}

/** Parse a flash offset like `0x10000`, `0X1000` or `4096`. */
fun parseOffset(s: String): Long = if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toLong(16) else s.toLong()

/**
 * Open the port, connect into the bootloader, optionally load the stub and raise the baud,
 * run [block] with a connected [EspLoader], then reset the chip with the `--after` strategy.
 */
fun GlobalOptions.withLoader(
    loadStub: Boolean = useStub,
    switchBaud: Boolean = true,
    resetAfter: Boolean = true,
    block: (EspLoader) -> Unit,
) {
    val portName = port ?: error("--port is required for this command (e.g. --port /dev/ttyUSB0)")
    val transport = JSerialCommTransport.open(portName, INITIAL_BAUD)
    val logger = ConsoleLogger(trace)
    try {
        val loader = EspLoader(transport, logger)
        if (trace) loader.enableTrace()
        loader.connect(beforeStrategy(before))
        if (loadStub) loader.runStub()
        if (switchBaud && baud != INITIAL_BAUD) loader.changeBaud(baud)
        block(loader)
        if (resetAfter) loader.hardReset(afterStrategy(after))
    } finally {
        transport.close()
    }
}

/** Global options carried by the root command and read by every subcommand. */
interface GlobalOptions {
    val port: String?
    val baud: Int
    val chip: String?
    val before: String
    val after: String
    val useStub: Boolean
    val trace: Boolean
}
