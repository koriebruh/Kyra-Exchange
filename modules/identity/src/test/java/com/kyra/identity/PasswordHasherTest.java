package com.kyra.identity;

import com.kyra.identity.domain.PasswordHasher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void verifiesCorrectPassword() {
        String encoded = hasher.hash("correct horse battery staple".toCharArray());
        assertTrue(hasher.verify("correct horse battery staple".toCharArray(), encoded));
    }

    @Test
    void rejectsWrongPassword() {
        String encoded = hasher.hash("s3cret-passphrase".toCharArray());
        assertFalse(hasher.verify("s3cret-passphras".toCharArray(), encoded));
        assertFalse(hasher.verify("".toCharArray(), encoded));
    }

    @Test
    void saltMakesHashesUnique() {
        String a = hasher.hash("same-password".toCharArray());
        String b = hasher.hash("same-password".toCharArray());
        assertNotEquals(a, b);
        assertTrue(hasher.verify("same-password".toCharArray(), a));
        assertTrue(hasher.verify("same-password".toCharArray(), b));
    }

    @Test
    void rejectsMalformedEncoding() {
        assertFalse(hasher.verify("x".toCharArray(), "not-a-valid-phc-string"));
        assertFalse(hasher.verify("x".toCharArray(), "$argon2id$broken"));
    }
}
