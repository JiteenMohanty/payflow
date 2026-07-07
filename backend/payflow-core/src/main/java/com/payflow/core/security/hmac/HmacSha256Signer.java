package com.payflow.core.security.hmac;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HmacSha256Signer implements HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    @Override
    public String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    @Override
    public boolean verify(String secret, String payload, String expectedHexDigest) {
        // Constant-time comparison - MessageDigest.isEqual is specifically
        // designed not to short-circuit on the first differing byte, so
        // this doesn't leak timing information about how much of the
        // signature was correct.
        String actual = sign(secret, payload);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8), expectedHexDigest.getBytes(StandardCharsets.UTF_8));
    }
}
