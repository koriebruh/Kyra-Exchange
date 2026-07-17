package com.kyra.identity.domain;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238) for two-factor auth (kyra-doc/modules/01, F3): HMAC-SHA1,
 * 30-second step, 6 digits, ±1 step of clock-drift tolerance. Codes are
 * single-use — the caller records the accepted step to block replay.
 */
@ApplicationScoped
public class TotpService {

    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20; // 160-bit, per spec
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom random = new SecureRandom();

    /** A new base32-encoded 160-bit secret. */
    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Current time step, so callers can persist it for anti-replay. */
    public long currentStep() {
        return Instant.now().getEpochSecond() / STEP_SECONDS;
    }

    /**
     * Verify a code within the drift window, rejecting steps at or before
     * {@code lastUsedStep} (single-use).
     *
     * @return the accepted step, or -1 if invalid
     */
    public long verify(String base32Secret, String code, long lastUsedStep) {
        if (code == null || code.length() != DIGITS) {
            return -1;
        }
        byte[] key = base32Decode(base32Secret);
        long now = currentStep();
        for (long step = now - WINDOW; step <= now + WINDOW; step++) {
            if (step <= lastUsedStep) {
                continue;
            }
            if (constantTimeEquals(generate(key, step), code)) {
                return step;
            }
        }
        return -1;
    }

    /** otpauth:// provisioning URI for authenticator apps / QR codes. */
    public String provisioningUri(String base32Secret, String accountEmail, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        String iss = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d"
                .formatted(label, base32Secret, iss, DIGITS, STEP_SECONDS);
    }

    /** Compute the code for a given step (package-visible for tests). */
    String generate(byte[] key, long step) {
        byte[] data = new byte[8];
        long value = step;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    String currentCodeFor(String base32Secret) {
        return generate(base32Decode(base32Secret), currentStep());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // ----- base32 (RFC 4648, no padding) -----

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(BASE32.charAt((buffer >> bits) & 0x1F));
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.trim().toUpperCase().replace("=", "");
        int buffer = 0;
        int bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("invalid base32 character");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
