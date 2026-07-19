package com.kyra.wallet.infra;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Computes the HMAC signature for Fystack Apex API requests
 * (docs.fystack.io/authentication). The scheme, verbatim from the docs:
 *
 * <ol>
 *   <li>build a canonical string
 *       {@code method={METHOD}&path={PATH}&timestamp={TIMESTAMP}&body={BODY}};</li>
 *   <li>HMAC-SHA256 it with the API secret;</li>
 *   <li>hex-encode the digest, then Base64-encode that hex string.</li>
 * </ol>
 *
 * The result goes in the {@code ACCESS-SIGN} header alongside {@code ACCESS-API-KEY}
 * and {@code ACCESS-TIMESTAMP}.
 *
 * <p><b>Verify against a live Apex instance:</b> the exact {@code PATH} convention
 * (whether it includes the {@code /api/v1} prefix and/or the query string) is not
 * fully pinned by the public docs. This class signs whatever {@code path} the
 * caller passes; {@link HttpCustodyProvider} passes the path-with-query. Confirm
 * the convention against a running Fystack before enabling in production
 * (kyra-doc/TECHDEBT.md).
 */
public final class FystackSigner {

    private FystackSigner() {
    }

    /** The Base64-of-hex HMAC-SHA256 signature for the canonical request string. */
    public static String sign(String secret, String method, String path, String timestamp, String body) {
        String canonical = "method=" + method
                + "&path=" + path
                + "&timestamp=" + timestamp
                + "&body=" + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to sign Fystack request", e);
        }
    }
}
