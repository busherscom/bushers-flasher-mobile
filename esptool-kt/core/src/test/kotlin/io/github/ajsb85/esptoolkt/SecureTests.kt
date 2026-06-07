package io.github.ajsb85.esptoolkt

import io.github.ajsb85.esptoolkt.efuse.BurnConfirmation
import io.github.ajsb85.esptoolkt.protocol.EspException
import io.github.ajsb85.esptoolkt.secure.FlashEncryption
import io.github.ajsb85.esptoolkt.secure.SecureBootV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureBootV2Test {
    @Test fun signThenVerifyRoundTrips() {
        val key = SecureBootV2.generateSigningKeyPem()
        val image = ByteArray(8000) { (it % 251).toByte() }
        val signed = SecureBootV2.sign(image, key)
        // image is sector-padded (8000 -> 8192) + 4096 signature sector
        assertEquals(8192 + 4096, signed.size)
        val result = SecureBootV2.verify(signed)
        assertTrue(result.ok)
        assertTrue(result.digestMatches)
        assertTrue(result.signatureValid)
    }

    @Test fun tamperedImageFailsVerification() {
        val key = SecureBootV2.generateSigningKeyPem()
        val signed = SecureBootV2.sign(ByteArray(4096) { 1 }, key).copyOf()
        signed[10] = (signed[10] + 1).toByte() // corrupt a content byte
        val result = SecureBootV2.verify(signed)
        assertFalse(result.ok)
    }

    @Test fun publicKeyRoundTrips() {
        val key = SecureBootV2.generateSigningKeyPem()
        val pub = SecureBootV2.publicKeyPem(key)
        assertTrue(pub.contains("BEGIN PUBLIC KEY"))
        SecureBootV2.loadPublicKey(pub) // must parse
    }
}

class FlashEncryptionTest {
    @Test fun generatesRequestedKeySize() {
        assertEquals(32, FlashEncryption.generateKey(256).size)
        assertEquals(64, FlashEncryption.generateKey(512).size)
    }
}

class BurnConfirmationTest {
    @Test fun rejectsWrongPhrase() {
        assertFailsWith<EspException> { BurnConfirmation.of("yes") }
        BurnConfirmation.of(BurnConfirmation.PHRASE) // must not throw
    }
}
