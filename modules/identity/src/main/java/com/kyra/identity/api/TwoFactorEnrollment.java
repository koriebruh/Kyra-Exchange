package com.kyra.identity.api;

import java.util.List;

/**
 * Enrollment material shown to the user once (kyra-doc/modules/01, F3).
 *
 * @param secret          base32 TOTP secret (also encoded in the URI)
 * @param provisioningUri otpauth:// URI for the QR code
 * @param recoveryCodes   one-time recovery codes — shown once, stored hashed
 */
public record TwoFactorEnrollment(String secret, String provisioningUri, List<String> recoveryCodes) {
}
