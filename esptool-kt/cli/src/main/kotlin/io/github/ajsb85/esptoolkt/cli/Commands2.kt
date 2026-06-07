package io.github.ajsb85.esptoolkt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.github.ajsb85.esptoolkt.flasher.FlashSegment
import io.github.ajsb85.esptoolkt.image.EspImage
import io.github.ajsb85.esptoolkt.image.FlashFreq
import io.github.ajsb85.esptoolkt.image.FlashMode
import io.github.ajsb85.esptoolkt.image.FlashSettings
import io.github.ajsb85.esptoolkt.image.FlashSize
import io.github.ajsb85.esptoolkt.image.MergeBin
import io.github.ajsb85.esptoolkt.targets.Chips
import java.io.File

class ReadMem(private val g: GlobalOptions) : CliktCommand(name = "read_mem") {
    override fun help(context: Context) = "Read a 32-bit word at <address>."
    private val addr: String by argument(name = "address")
    override fun run() = g.withLoader(loadStub = false) {
        println("0x%08x = 0x%08x".format(parseOffset(addr), it.readMem(parseOffset(addr))))
    }
}

class WriteMem(private val g: GlobalOptions) : CliktCommand(name = "write_mem") {
    override fun help(context: Context) = "Write a 32-bit <value> at <address>."
    private val addr: String by argument(name = "address")
    private val value: String by argument(name = "value")
    override fun run() = g.withLoader(loadStub = false) {
        it.writeMem(parseOffset(addr), parseOffset(value))
        println("Wrote 0x%08x to 0x%08x".format(parseOffset(value), parseOffset(addr)))
    }
}

class DumpMem(private val g: GlobalOptions) : CliktCommand(name = "dump_mem") {
    override fun help(context: Context) = "Dump <size> bytes from memory <address> to <file>."
    private val addr: String by argument(name = "address")
    private val size: String by argument(name = "size")
    private val out: String by argument(name = "file")
    override fun run() = g.withLoader(loadStub = false) {
        val data = it.dumpMem(parseOffset(addr), parseOffset(size).toInt())
        File(out).writeBytes(data)
        println("Dumped ${data.size} bytes from 0x%x to $out".format(parseOffset(addr)))
    }
}

class ReadFlashStatus(private val g: GlobalOptions) : CliktCommand(name = "read_flash_status") {
    override fun help(context: Context) = "Read the SPI flash status register."
    private val bytes: Int by option("--bytes", help = "Status bytes to read (1-3)").int().default(2)
    override fun run() = g.withLoader {
        println("Flash status: 0x%06x".format(it.readFlashStatus(bytes)))
    }
}

class WriteFlashStatus(private val g: GlobalOptions) : CliktCommand(name = "write_flash_status") {
    override fun help(context: Context) = "Write the SPI flash status register (controls protection bits)."
    private val bytes: Int by option("--bytes").int().default(2)
    private val nonVolatile: Boolean by option("--non-volatile").flag(default = false)
    private val value: String by argument(name = "value")
    override fun run() = g.withLoader {
        it.writeFlashStatus(parseOffset(value), bytes, nonVolatile)
        println("Flash status now: 0x%06x".format(it.readFlashStatus(bytes)))
    }
}

class GetSecurityInfo(private val g: GlobalOptions) : CliktCommand(name = "get_security_info") {
    override fun help(context: Context) = "Report secure boot / flash encryption / JTAG-USB lockdown status."
    override fun run() = g.withLoader(loadStub = false, switchBaud = false) {
        val info = it.securityInfo()
        if (info == null) {
            println("GET_SECURITY_INFO is not supported by ${it.chip?.name}")
        } else {
            println(info.summary())
        }
    }
}

class LoadRam(private val g: GlobalOptions) : CliktCommand(name = "load_ram") {
    override fun help(context: Context) = "Load an image into RAM and execute it (bypasses flash)."
    private val file: String by argument(name = "file")
    override fun run() = g.withLoader(loadStub = false, switchBaud = false, resetAfter = false) {
        val hasExt = it.chip != Chips.ESP8266
        it.loadRam(EspImage.parse(File(file).readBytes(), hasExt))
        println("Loaded ${File(file).name} into RAM and started it")
    }
}

class MergeBinCmd : CliktCommand(name = "merge_bin") {
    override fun help(context: Context) = "Merge <offset> <file> pairs into one binary (no device needed)."
    private val output: String by option("-o", "--output", help = "Output file").required()
    private val flashMode: String? by option("--flash-mode", "-fm")
    private val flashSize: String? by option("--flash-size", "-fs")
    private val flashFreq: String? by option("--flash-freq", "-ff")
    private val bootloaderOffset: String by option("--bootloader-offset").default("0x1000")
    private val targetOffset: String? by option("--target-offset")
    private val args: List<String> by argument(name = "<offset> <file> ...").multiple(required = true)
    override fun run() {
        require(args.size % 2 == 0) { "merge_bin expects <offset> <file> pairs" }
        val segments = args.chunked(2).map { (off, path) ->
            FlashSegment(parseOffset(off), File(path).readBytes(), name = File(path).name)
        }
        val settings = FlashSettings(
            mode = flashMode?.let { FlashMode.from(it) },
            size = flashSize?.let { FlashSize.from(it) },
            freq = flashFreq?.let { FlashFreq.from(it) },
        )
        val merged = MergeBin.mergeRaw(
            segments,
            settings,
            bootloaderOffset = parseOffset(bootloaderOffset),
            targetOffset = targetOffset?.let { parseOffset(it) },
        )
        File(output).writeBytes(merged)
        println("Merged ${segments.size} files into $output (${merged.size} bytes)")
    }
}
