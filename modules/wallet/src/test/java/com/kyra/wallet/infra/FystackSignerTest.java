package com.kyra.wallet.infra;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Fystack HMAC signer against an independent reference. The expected
 * value below was produced with OpenSSL (not this code):
 * <pre>
 * CANON='method=GET&path=/wallets/w1/deposit-address&timestamp=1700000000&body='
 * printf '%s' "$CANON" | openssl dgst -sha256 -hmac 'test-secret-123'  # -> hex
 * printf '%s' "$hex" | base64                                          # -> ACCESS-SIGN
 * </pre>
 * so a green test proves the Java implementation matches the documented scheme,
 * not merely itself.
 */
class FystackSignerTest {

    private static final String SECRET = "test-secret-123";

    @Test
    void matchesOpensslReferenceVector() {
        String sign = FystackSigner.sign(SECRET, "GET",
                "/wallets/w1/deposit-address", "1700000000", "");
        assertEquals(
                "ZGFkNWMwZWE2ODk4NTNkYmE2N2VjNmUyMGM0OGRiNDE2MDJkNjU0MmFlMDU5YzM0MGUzM2I5N2NlYTA5MDRjNg==",
                sign);
    }

    @Test
    void signatureIsBase64OfA64CharHexDigest() {
        String sign = FystackSigner.sign(SECRET, "POST", "/wallets/w1/request-withdrawal",
                "1700000001", "{\"amount\":\"5\"}");
        String hex = new String(Base64.getDecoder().decode(sign));
        assertEquals(64, hex.length(), "SHA-256 digest is 32 bytes = 64 hex chars");
        assertTrue(hex.matches("[0-9a-f]{64}"), "lower-case hex");
    }

    @Test
    void isDeterministic() {
        assertEquals(
                FystackSigner.sign(SECRET, "GET", "/x", "1", "b"),
                FystackSigner.sign(SECRET, "GET", "/x", "1", "b"));
    }

    @Test
    void anyChangedFieldChangesTheSignature() {
        String base = FystackSigner.sign(SECRET, "GET", "/x", "1", "b");
        assertNotEquals(base, FystackSigner.sign("other-secret", "GET", "/x", "1", "b"));
        assertNotEquals(base, FystackSigner.sign(SECRET, "POST", "/x", "1", "b"));
        assertNotEquals(base, FystackSigner.sign(SECRET, "GET", "/y", "1", "b"));
        assertNotEquals(base, FystackSigner.sign(SECRET, "GET", "/x", "2", "b"));
        assertNotEquals(base, FystackSigner.sign(SECRET, "GET", "/x", "1", "b2"));
    }

    @Test
    void nullBodyIsTreatedAsEmpty() {
        assertEquals(
                FystackSigner.sign(SECRET, "GET", "/x", "1", ""),
                FystackSigner.sign(SECRET, "GET", "/x", "1", null));
    }
}
