package com.kyra.identity.domain;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    @Test
    void rfc6238ReferenceVector() {
        // RFC 6238 Appendix B: ASCII secret "12345678901234567890", T=59s (step 1),
        // SHA1, 8 digits -> 94287082; truncated to 6 digits -> 287082.
        byte[] key = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("287082", totp.generate(key, 1L));
    }

    @Test
    void currentCodeVerifiesAndIsSingleUse() {
        String secret = totp.newSecret();
        String code = totp.currentCodeFor(secret);

        long step = totp.verify(secret, code, -1);
        assertTrue(step > 0, "valid current code must be accepted");

        // replay with lastUsedStep = step must be rejected
        assertEquals(-1, totp.verify(secret, code, step), "used code must not replay");
    }

    @Test
    void wrongCodeRejected() {
        String secret = totp.newSecret();
        assertEquals(-1, totp.verify(secret, "000000", -1));
        assertEquals(-1, totp.verify(secret, "12", -1));
        assertEquals(-1, totp.verify(secret, null, -1));
    }

    @Test
    void secretsAreRandom() {
        assertNotEquals(totp.newSecret(), totp.newSecret());
    }

    @Test
    void base32RoundTrips() {
        byte[] original = {0, 1, 2, 3, (byte) 200, (byte) 255, 42, 17};
        assertEquals(java.util.Arrays.toString(original),
                java.util.Arrays.toString(TotpService.base32Decode(TotpService.base32Encode(original))));
    }

    @Test
    void provisioningUriWellFormed() {
        String uri = totp.provisioningUri("ABCDEF", "user@kyra.test", "Kyra");
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=ABCDEF"));
        assertTrue(uri.contains("issuer=Kyra"));
    }
}
