package io.github.ajsb85.esptoolkt.secure

import java.security.SecureRandom

/**
 * Flash-encryption key material — the safe, host-side parts of `espsecure.py`.
 *
 * Generates a random AES key for flash encryption (`generate_flash_encryption_key`). The actual
 * AES-XTS encrypt/decrypt of flash data is intentionally not implemented yet (it is chip-version
 * specific and not required to *provision* a key); it is tracked as a follow-up.
 */
object FlashEncryption {
    /**
     * Generate a random flash-encryption key. [bits] is 256 for most chips (AES-256 key block)
     * or 512 for ESP32 with the longer key option.
     */
    fun generateKey(bits: Int = 256): ByteArray {
        require(bits == 128 || bits == 256 || bits == 512) { "Key size must be 128, 256, or 512 bits" }
        return ByteArray(bits / 8).also { SecureRandom().nextBytes(it) }
    }
}
