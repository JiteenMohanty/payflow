package com.payflow.core.security.hmac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256SignerTest {

    private final HmacSha256Signer signer = new HmacSha256Signer();

    @Test
    void signingTheSamePayloadWithTheSameSecretIsDeterministic() {
        String digest1 = signer.sign("secret", "hello world");
        String digest2 = signer.sign("secret", "hello world");

        assertThat(digest1).isEqualTo(digest2);
        assertThat(digest1).matches("[0-9a-f]{64}");
    }

    @Test
    void differentSecretsProduceDifferentDigests() {
        String digest1 = signer.sign("secret-a", "hello world");
        String digest2 = signer.sign("secret-b", "hello world");

        assertThat(digest1).isNotEqualTo(digest2);
    }

    @Test
    void verifyAcceptsAMatchingDigest() {
        String digest = signer.sign("secret", "payload");

        assertThat(signer.verify("secret", "payload", digest)).isTrue();
    }

    @Test
    void verifyRejectsATamperedPayload() {
        String digest = signer.sign("secret", "payload");

        assertThat(signer.verify("secret", "a different payload", digest)).isFalse();
    }

    @Test
    void verifyRejectsTheWrongSecret() {
        String digest = signer.sign("secret", "payload");

        assertThat(signer.verify("wrong-secret", "payload", digest)).isFalse();
    }
}
