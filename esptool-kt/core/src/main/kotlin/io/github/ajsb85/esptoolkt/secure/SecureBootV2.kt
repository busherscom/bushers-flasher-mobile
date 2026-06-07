package io.github.ajsb85.esptoolkt.secure

import io.github.ajsb85.esptoolkt.protocol.EspException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.CRC32

/**
 * Secure Boot V2 (RSA-3072) signing/verification — the Kotlin equivalent of `espsecure.py
 * sign_data --version 2` for the RSA scheme used by ESP32-S2/S3/C3 and later.
 *
 * The 1216-byte signature block layout is taken verbatim from espsecure's
 * `generate_rsa_signature_block`:
 * `magic(1)=0xE7 | version(1)=0x02 | pad(2) | sha256(32) | n_LE(384) | e(4) | rinv_LE(384) |
 *  m'(4) | signature_LE(384) | crc32(4) | pad(16)`, then the block is padded with `0xFF` to a
 * 4096-byte signature sector and appended after the (sector-padded) image.
 *
 * This is host-side cryptography (no chip involved) — entirely safe. Signatures are randomized
 * (PSS salt), so they are validated by verification, not byte-equality; they interoperate with
 * `espsecure.py verify_signature`.
 */
object SecureBootV2 {
    private const val MAGIC = 0xE7
    private const val VERSION_RSA = 0x02
    private const val SIG_BLOCK_SIZE = 1216
    private const val SECTOR_SIZE = 4096
    private const val RSA_KEY_BITS = 3072
    private const val RSA_BYTES = RSA_KEY_BITS / 8 // 384
    private const val SALT_LEN = 32

    /** Generate an RSA-3072 signing key and return it as a PKCS#8 PEM string. */
    fun generateSigningKeyPem(): String {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(RSA_KEY_BITS)
        val priv = kpg.generateKeyPair().private
        return pem("PRIVATE KEY", priv.encoded)
    }

    /** Export the public key (X.509 SubjectPublicKeyInfo PEM) of a PKCS#8 private-key PEM. */
    fun publicKeyPem(privatePem: String): String {
        val priv = loadPrivateKey(privatePem)
        val pub = publicFromPrivate(priv)
        return pem("PUBLIC KEY", pub.encoded)
    }

    /**
     * Sign [image] with Secure Boot V2 and return `image (sector-padded) + signature sector`.
     * Mirrors `espsecure.py sign_data --version 2 --keyfile <pem>`.
     */
    fun sign(image: ByteArray, privatePem: String): ByteArray {
        val priv = loadPrivateKey(privatePem)
        val pub = publicFromPrivate(priv)
        val contents = padToSector(image)
        val digest = sha256(contents)

        val signer = Signature.getInstance("RSASSA-PSS")
        signer.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, SALT_LEN, 1))
        signer.initSign(priv)
        signer.update(contents)
        val signature = signer.sign() // big-endian, RSA_BYTES long

        val block = buildBlock(digest, pub, signature)
        val sector = block.copyOf(SECTOR_SIZE).also { java.util.Arrays.fill(it, block.size, SECTOR_SIZE, 0xFF.toByte()) }
        return contents + sector
    }

    /** Result of [verify]. */
    data class VerifyResult(val digestMatches: Boolean, val signatureValid: Boolean) {
        val ok: Boolean get() = digestMatches && signatureValid
    }

    /** Verify a Secure Boot V2 signed image. The public key is recovered from the block itself. */
    fun verify(signed: ByteArray): VerifyResult {
        if (signed.size < SECTOR_SIZE * 2) throw EspException("Signed image too small")
        val contents = signed.copyOf(signed.size - SECTOR_SIZE)
        val block = signed.copyOfRange(signed.size - SECTOR_SIZE, signed.size - SECTOR_SIZE + SIG_BLOCK_SIZE)
        if ((block[0].toInt() and 0xFF) != MAGIC || (block[1].toInt() and 0xFF) != VERSION_RSA) {
            throw EspException("Not an RSA Secure Boot V2 signature block")
        }
        val storedDigest = block.copyOfRange(4, 36)
        val recomputed = sha256(contents)
        val digestMatches = storedDigest.contentEquals(recomputed)

        val n = leToBigInteger(block, 36, RSA_BYTES)
        val e = leToBigInteger(block, 36 + RSA_BYTES, 4)
        val sigLe = block.copyOfRange(812, 812 + RSA_BYTES)
        val signature = sigLe.reversedArray()

        val pub = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e)) as RSAPublicKey
        val verifier = Signature.getInstance("RSASSA-PSS")
        verifier.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, SALT_LEN, 1))
        verifier.initVerify(pub)
        verifier.update(contents)
        val signatureValid = verifier.verify(signature)
        return VerifyResult(digestMatches, signatureValid)
    }

    private fun buildBlock(digest: ByteArray, pub: RSAPublicKey, signature: ByteArray): ByteArray {
        val n = pub.modulus
        val e = pub.publicExponent.toInt()
        val r32 = BigInteger.ONE.shiftLeft(32)
        val nInv = n.mod(r32).modInverse(r32)
        val mPrime = r32.subtract(nInv).mod(r32).toLong() // -(n^-1) mod 2^32
        val rinv = BigInteger.ONE.shiftLeft(RSA_KEY_BITS * 2).mod(n)

        val out = java.io.ByteArrayOutputStream(SIG_BLOCK_SIZE)
        out.write(MAGIC)
        out.write(VERSION_RSA)
        out.write(0)
        out.write(0)
        out.write(digest)
        out.write(toFixedLe(n, RSA_BYTES))
        out.write(u32le(e.toLong()))
        out.write(toFixedLe(rinv, RSA_BYTES))
        out.write(u32le(mPrime))
        out.write(signature.reversedArray())
        val body = out.toByteArray()
        val crc = CRC32().apply { update(body) }.value
        return body + u32le(crc) + ByteArray(16)
    }

    // --- helpers ---

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)

    private fun padToSector(data: ByteArray): ByteArray {
        val rem = data.size % SECTOR_SIZE
        if (rem == 0) return data
        return data.copyOf(data.size + (SECTOR_SIZE - rem)).also {
            java.util.Arrays.fill(it, data.size, it.size, 0xFF.toByte())
        }
    }

    private fun u32le(v: Long) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    /** BigInteger → fixed-length big-endian, then reversed to little-endian. */
    private fun toFixedLe(value: BigInteger, len: Int): ByteArray {
        var be = value.toByteArray()
        if (be.size > len) be = be.copyOfRange(be.size - len, be.size) // drop sign byte / overflow
        val fixed = ByteArray(len)
        be.copyInto(fixed, len - be.size)
        return fixed.reversedArray()
    }

    private fun leToBigInteger(b: ByteArray, off: Int, len: Int): BigInteger = BigInteger(1, b.copyOfRange(off, off + len).reversedArray())

    private fun pem(type: String, der: ByteArray): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $type-----\n$b64\n-----END $type-----\n"
    }

    fun loadPrivateKey(privatePem: String): RSAPrivateCrtKey {
        val der = derFromPem(privatePem)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        return key as? RSAPrivateCrtKey ?: throw EspException("Not an RSA private key (CRT form required)")
    }

    private fun publicFromPrivate(priv: RSAPrivateCrtKey): RSAPublicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)) as RSAPublicKey

    fun loadPublicKey(pem: String): RSAPublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(derFromPem(pem))) as RSAPublicKey

    private fun derFromPem(pem: String): ByteArray {
        val b64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") || it.isBlank() }
            .joinToString("")
        return Base64.getMimeDecoder().decode(b64)
    }
}
