package com.kyra.identity.domain;

import jakarta.enterprise.context.ApplicationScoped;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id password hashing (kyra-doc/modules/01, F1). Produces and verifies a
 * self-describing PHC string, so parameters can evolve without a migration:
 * {@code $argon2id$v=19$m=65536,t=3,p=2$<salt-b64>$<hash-b64>}.
 *
 * <p>Defaults: 64 MiB memory, 3 iterations, parallelism 2 — tune per hardware.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int MEMORY_KIB = 64 * 1024;
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 2;
    private static final int SALT_LEN = 16;
    private static final int HASH_LEN = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
    private final Base64.Decoder b64dec = Base64.getDecoder();

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_LEN];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM);
        return "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s".formatted(
                MEMORY_KIB, ITERATIONS, PARALLELISM, b64.encodeToString(salt), b64.encodeToString(hash));
    }

    /** Constant-time verification. Returns false on any malformed input. */
    public boolean verify(char[] password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            // ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
            if (parts.length != 6 || !parts[1].equals("argon2id")) {
                return false;
            }
            String[] params = parts[3].split(",");
            int memory = Integer.parseInt(params[0].substring(2));
            int iterations = Integer.parseInt(params[1].substring(2));
            int parallelism = Integer.parseInt(params[2].substring(2));
            byte[] salt = b64dec.decode(parts[4]);
            byte[] expected = b64dec.decode(parts[5]);
            byte[] actual = derive(password, salt, memory, iterations, parallelism);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int memory, int iterations, int parallelism) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[HASH_LEN];
        byte[] pwd = toBytes(password);
        try {
            generator.generateBytes(pwd, out);
        } finally {
            java.util.Arrays.fill(pwd, (byte) 0);
        }
        return out;
    }

    private static byte[] toBytes(char[] chars) {
        return new String(chars).getBytes(StandardCharsets.UTF_8);
    }
}
