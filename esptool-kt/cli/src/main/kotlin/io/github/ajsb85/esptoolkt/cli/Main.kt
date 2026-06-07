package io.github.ajsb85.esptoolkt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.flasher.Flasher
import io.github.ajsb85.esptoolkt.image.FirmwareImage
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import java.io.File

private const val VERSION = "0.1.0"

/** Root command holding the global options shared with every subcommand. */
class EspToolKt :
    CliktCommand(name = "esptool-kt"),
    GlobalOptions {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "A pure-Kotlin esptool: flash and inspect Espressif chips over serial."

    override val port: String? by option("-p", "--port", help = "Serial port, e.g. /dev/ttyUSB0")
    override val baud: Int by option("-b", "--baud", help = "Flashing baud rate").int().default(460800)
    override val chip: String? by option("--chip", help = "Target chip (default: auto-detect)")
    override val before: String by option("--before", help = "Reset before: default_reset|usb_reset|no_reset").default("default_reset")
    override val after: String by option("--after", help = "Reset after: hard_reset|no_reset").default("hard_reset")
    private val noStub: Boolean by option("--no-stub", help = "Do not load the flasher stub").flag(default = false)
    override val trace: Boolean by option("--trace", help = "Print a hex wire trace").flag(default = false)

    override val useStub: Boolean get() = !noStub

    override fun run() = Unit
}

class WriteFlash(private val g: GlobalOptions) : CliktCommand(name = "write_flash") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Write <offset> <file> pairs to flash (with verification)."

    private val flashMode: String? by option("--flash-mode", "-fm")
    private val flashSize: String? by option("--flash-size", "-fs")
    private val flashFreq: String? by option("--flash-freq", "-ff")
    private val noCompress: Boolean by option("-u", "--no-compress").flag(default = false)
    private val noVerify: Boolean by option("--no-verify").flag(default = false)
    private val args: List<String> by argument(name = "<offset> <file> ...").multiple(required = true)

    override fun run() {
        require(args.size % 2 == 0) { "write_flash expects <offset> <file> pairs" }
        val segments = args.chunked(2).map { (off, path) ->
            val file = File(path)
            require(file.isFile) { "No such file: $path" }
            FlashSegment(parseOffset(off), file.readBytes(), name = file.name)
        }
        val settings = FlashSettings(
            mode = flashMode?.let { FlashMode.from(it) },
            size = flashSize?.let { FlashSize.from(it) },
            freq = flashFreq?.let { FlashFreq.from(it) },
        )
        g.withLoader { loader ->
            Flasher(loader, loader.logger).writeFlash(segments, settings, compress = !noCompress, verify = !noVerify)
        }
    }
}

class ReadFlash(private val g: GlobalOptions) : CliktCommand(name = "read_flash") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Read flash: <offset> <size> <file>."
    private val offsetArg: String by argument(name = "offset")
    private val sizeArg: String by argument(name = "size")
    private val outFile: String by argument(name = "file")
    override fun run() {
        g.withLoader { loader ->
            val data = loader.readFlash(parseOffset(offsetArg), parseOffset(sizeArg).toInt())
            File(outFile).writeBytes(data)
            println("Wrote ${data.size} bytes to $outFile")
        }
    }
}

class EraseFlash(private val g: GlobalOptions) : CliktCommand(name = "erase_flash") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Erase the entire flash."
    override fun run() = g.withLoader {
        it.eraseFlash()
        println("Flash erased")
    }
}

class EraseRegion(private val g: GlobalOptions) : CliktCommand(name = "erase_region") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Erase a region: <offset> <size> (4 KB aligned)."
    private val offsetArg: String by argument(name = "offset")
    private val sizeArg: String by argument(name = "size")
    override fun run() = g.withLoader {
        it.eraseRegion(parseOffset(offsetArg), parseOffset(sizeArg))
        println("Region erased")
    }
}

class VerifyFlash(private val g: GlobalOptions) : CliktCommand(name = "verify_flash") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Verify <offset> <file> pairs against flash."
    private val args: List<String> by argument(name = "<offset> <file> ...").multiple(required = true)
    override fun run() {
        require(args.size % 2 == 0) { "verify_flash expects <offset> <file> pairs" }
        g.withLoader { loader ->
            val flasher = Flasher(loader, loader.logger)
            args.chunked(2).forEach { (off, path) ->
                val ok = flasher.verifyFlash(parseOffset(off), File(path).readBytes())
                println("${if (ok) "OK   " else "FAIL "} $path @ $off")
            }
        }
    }
}

class FlashId(private val g: GlobalOptions) : CliktCommand(name = "flash_id") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Read SPI flash manufacturer/device ID and size."
    override fun run() = g.withLoader { loader ->
        val id = loader.readFlashId()
        println("Manufacturer: %02x".format(id and 0xFF))
        println("Device:       %02x%02x".format((id shr 8) and 0xFF, (id shr 16) and 0xFF))
        loader.detectFlashSize()?.let { println("Detected size: ${it / (1024 * 1024)} MB") }
    }
}

class ChipId(private val g: GlobalOptions) : CliktCommand(name = "chip_id") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Detect the chip and print its identity + MAC."
    override fun run() = g.withLoader { loader ->
        println("Chip: ${loader.chip?.name}")
        runCatching { loader.readMac() }.onSuccess { mac ->
            println("MAC:  " + mac.joinToString(":") { "%02x".format(it) })
        }
    }
}

class ReadMac(private val g: GlobalOptions) : CliktCommand(name = "read_mac") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Read the factory MAC address."
    override fun run() = g.withLoader { loader ->
        println("MAC: " + loader.readMac().joinToString(":") { "%02x".format(it) })
    }
}

class ImageInfo : CliktCommand(name = "image_info") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Print structure of a local firmware image (no device)."
    private val esp8266: Boolean by option("--esp8266", help = "Parse as an ESP8266 image (no extended header)").flag()
    private val file: String by argument(name = "file")
    override fun run() {
        val data = File(file).readBytes()
        // Prefer the full segment-aware parser; fall back to the basic header summary.
        val out = runCatching {
            io.github.ajsb85.esptoolkt.image.EspImage.parse(data, hasExtendedHeader = !esp8266).describe()
        }.getOrElse { FirmwareImage.describe(data) }
        println(out)
    }
}

class Run(private val g: GlobalOptions) : CliktCommand(name = "run") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Reset the chip and run the application."
    override fun run() = g.withLoader { println("Chip reset; running application") }
}

class Version : CliktCommand(name = "version") {
    override fun help(context: com.github.ajalt.clikt.core.Context) = "Print the esptool-kt version."
    override fun run() = println("esptool-kt $VERSION")
}

fun main(argv: Array<String>) {
    val root = EspToolKt()
    root.subcommands(
        WriteFlash(root), ReadFlash(root), EraseFlash(root), EraseRegion(root),
        VerifyFlash(root), FlashId(root), ChipId(root), ReadMac(root),
        GetSecurityInfo(root), ReadFlashStatus(root), WriteFlashStatus(root),
        ReadMem(root), WriteMem(root), DumpMem(root), LoadRam(root),
        ImageInfo(), MergeBinCmd(), Run(root), Version(),
        // espefuse-style (device)
        EfuseSummary(root), BurnEfuse(root),
        // espsecure-style (host, no device)
        GenerateSigningKey(), SignData(), VerifySignature(), GenerateFlashEncryptionKey(),
    ).main(argv)
}
