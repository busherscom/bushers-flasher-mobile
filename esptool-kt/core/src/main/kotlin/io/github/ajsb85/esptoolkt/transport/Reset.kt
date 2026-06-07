package io.github.ajsb85.esptoolkt.transport

/**
 * Reset sequences that drive the board's DTR/RTS lines (wired to EN/IO0 on most
 * dev boards) to bring the chip into the serial download bootloader, or to reboot it.
 *
 * Ported from esptool-js `reset.ts` / esptool.py `reset.py`. Because these only use
 * [SerialTransport.setDtr]/[SerialTransport.setRts] and sleeping, they live in the core
 * and are reused verbatim by both the JVM and Android transports.
 */
fun interface ResetStrategy {
    fun reset(transport: SerialTransport)
}

private fun sleep(ms: Long) = Thread.sleep(ms)

/** Classic auto-reset (DTR→IO0, RTS→EN). This is what CP210x/CH340/FT232 boards use. */
class ClassicReset(
    private val resetDelayMs: Long = 50,
    private val flowControl: Boolean = false
) : ResetStrategy {
    override fun reset(transport: SerialTransport) {
        transport.setDtr(false) // IO0 = HIGH
        transport.setRts(true) // EN = LOW, chip held in reset
        sleep(100)
        transport.setDtr(true) // IO0 = LOW (enter download mode)
        transport.setRts(false) // EN = HIGH, chip released
        sleep(resetDelayMs)
        if (!flowControl) {
            transport.setDtr(false) // release IO0
        }
    }
}

/** Reset sequence for chips connected via the built-in USB-JTAG-Serial peripheral. */
class UsbJtagReset : ResetStrategy {
    override fun reset(transport: SerialTransport) {
        transport.setRts(false)
        transport.setDtr(false)
        sleep(100)
        transport.setDtr(true)
        transport.setRts(false)
        sleep(100)
        transport.setRts(true)
        transport.setDtr(false)
        transport.setRts(true)
        sleep(100)
        transport.setRts(false)
        transport.setDtr(false)
    }
}

/**
 * Hard reset: pulse EN (RTS low→high) to reboot the chip out of the bootloader/stub and
 * into the application. Mirrors esptool.py `HardReset` (the initial RTS-assert is essential —
 * without it EN is never driven low and the chip is not actually reset).
 */
class HardReset(
    private val usingUsbOtg: Boolean = false,
    private val flowControl: Boolean = false
) : ResetStrategy {
    override fun reset(transport: SerialTransport) {
        if (flowControl) {
            transport.setDtr(false)
            transport.setRts(true)
            sleep(100)
            transport.setDtr(true)
            sleep(100)
            transport.setRts(false)
        } else {
            transport.setRts(true) // EN -> LOW (hold in reset)
            if (usingUsbOtg) {
                sleep(200)
                transport.setRts(false) // EN -> HIGH (run)
                sleep(200)
            } else {
                sleep(100)
                transport.setRts(false) // EN -> HIGH (run)
            }
        }
    }
}

/**
 * Custom reset described by a sequence string of `|`-separated commands:
 *  - `D0`/`D1`: set DTR low/high, `R0`/`R1`: set RTS low/high, `W<ms>`: wait.
 *
 * e.g. `"D0|R1|W100|D1|R0|W50|D0"` is the classic sequence.
 */
class CustomReset(private val sequence: String) : ResetStrategy {
    override fun reset(transport: SerialTransport) {
        for (cmd in sequence.split("|")) {
            if (cmd.isEmpty()) continue
            val arg = cmd.substring(1)
            when (cmd[0]) {
                'D' -> transport.setDtr(arg == "1")
                'R' -> transport.setRts(arg == "1")
                'W' -> sleep(arg.toLong())
                else -> error("Invalid reset command: $cmd")
            }
        }
    }
}
