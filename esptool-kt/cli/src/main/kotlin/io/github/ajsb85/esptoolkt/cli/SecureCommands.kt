package io.github.ajsb85.esptoolkt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.github.ajsb85.esptoolkt.secure.FlashEncryption
import io.github.ajsb85.esptoolkt.secure.SecureBootV2
import java.io.File

/** `espsecure`-style host-side crypto (no device). */

class GenerateSigningKey : CliktCommand(name = "generate_signing_key") {
    override fun help(context: Context) = "Generate a Secure Boot V2 RSA-3072 signing key (PEM)."
    private val output: String by option("-o", "--keyfile", help = "Output private key PEM").required()
    private val pubOut: String? by option("--pub-keyfile", help = "Also write the public key PEM")
    override fun run() {
        val pem = SecureBootV2.generateSigningKeyPem()
        File(output).writeText(pem)
        println("Wrote RSA-3072 private key to $output")
        pubOut?.let {
            File(it).writeText(SecureBootV2.publicKeyPem(pem))
            println("Wrote public key to $it")
        }
    }
}

class SignData : CliktCommand(name = "sign_data") {
    override fun help(context: Context) = "Sign an image with Secure Boot V2 (RSA-3072), appending a signature sector."
    private val keyfile: String by option("-k", "--keyfile", help = "Private key PEM").required()
    private val output: String by option("-o", "--output", help = "Signed output file").required()
    private val datafile: String by argument(name = "datafile")
    override fun run() {
        val signed = SecureBootV2.sign(File(datafile).readBytes(), File(keyfile).readText())
        File(output).writeBytes(signed)
        println("Signed $datafile -> $output (${signed.size} bytes, Secure Boot V2)")
    }
}

class VerifySignature : CliktCommand(name = "verify_signature") {
    override fun help(context: Context) = "Verify a Secure Boot V2 signed image (self-contained public key)."
    private val datafile: String by argument(name = "signed-image")
    override fun run() {
        val result = SecureBootV2.verify(File(datafile).readBytes())
        println("Image digest match: ${result.digestMatches}")
        println("Signature valid:    ${result.signatureValid}")
        println(if (result.ok) "VERIFIED" else "VERIFICATION FAILED")
    }
}

class GenerateFlashEncryptionKey : CliktCommand(name = "generate_flash_encryption_key") {
    override fun help(context: Context) = "Generate a random flash-encryption key."
    private val bits: Int by option("--bits", help = "Key size in bits (128/256/512)").int().default(256)
    private val output: String by argument(name = "keyfile")
    override fun run() {
        File(output).writeBytes(FlashEncryption.generateKey(bits))
        println("Wrote ${bits / 8}-byte flash-encryption key to $output")
    }
}
