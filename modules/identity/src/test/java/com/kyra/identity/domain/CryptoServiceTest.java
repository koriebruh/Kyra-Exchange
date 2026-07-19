package com.kyra.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for the at-rest secret cipher (AES-256-GCM). This protects TOTP
 * secrets and API-key secrets, so it is exercised for confidentiality
 * (round-trip), non-determinism (fresh IV per call), and — critically —
 * integrity: any tampering or wrong key must FAIL LOUD, never return wrong
 * plaintext silently.
 */
class CryptoServiceTest {

    private static String keyOf(byte fill) {
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final CryptoService crypto = new CryptoService(keyOf((byte) 7));

    @Test
    void roundTripsIncludingUnicodeAndEmpty() {
        for (String secret : new String[] { "JBSWY3DPEHPK3PXP", "", "π-λ-🔐 secret", "a".repeat(4096) }) {
            assertEquals(secret, crypto.decrypt(crypto.encrypt(secret)));
        }
    }

    @Test
    void ciphertextIsNotThePlaintext() {
        String secret = "top-secret-totp-seed";
        String enc = crypto.encrypt(secret);
        assertNotEquals(secret, enc);
        assertTrue(Base64.getDecoder().decode(enc).length > secret.length(),
                "output carries iv+tag overhead");
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        // Fresh random IV per call → identical plaintext must NOT yield identical
        // ciphertext (otherwise equal stored secrets would be linkable).
        String secret = "same-input";
        assertNotEquals(crypto.encrypt(secret), crypto.encrypt(secret));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] blob = Base64.getDecoder().decode(crypto.encrypt("integrity-matters"));
        blob[blob.length - 1] ^= 0x01; // flip one bit of the GCM tag/ciphertext
        String tampered = Base64.getEncoder().encodeToString(blob);
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String enc = crypto.encrypt("bound-to-its-key");
        CryptoService other = new CryptoService(keyOf((byte) 42));
        assertThrows(IllegalStateException.class, () -> other.decrypt(enc));
    }

    @Test
    void garbageInputIsRejectedNotSilentlyWrong() {
        assertThrows(RuntimeException.class, () -> crypto.decrypt("not-valid-base64!!!"));
        assertThrows(RuntimeException.class, () -> crypto.decrypt("c2hvcnQ=")); // valid b64, too short for iv+tag
    }

    @Test
    void nonAes256KeyRejectedAtConstruction() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // AES-128 length
        assertThrows(IllegalStateException.class, () -> new CryptoService(shortKey));
    }
}
