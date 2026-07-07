package com.payflow.mockprovider.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fires an async signed webhook after every charge operation, regardless of
 * the synchronous response - see EDD section 5.3 and ADR-0011. Fire-and-
 * forget on the common ForkJoinPool: this is a low-volume simulation
 * service, not worth a dedicated executor. No deliberate delay/failure
 * simulation - that's M11 (Mock Provider hardening), matching every other
 * endpoint's own "deterministic for now" scope.
 *
 * The signing here is a small, deliberate duplication of payflow-core's own
 * HmacSha256Signer - the two services are independent deployables with no
 * shared library between them by design, and this is ~10 lines of standard
 * JDK crypto, not worth a shared module for.
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String secret;

    public WebhookSender(
            @Value("${payflow.webhook.callback-base-url}") String callbackBaseUrl,
            @Value("${payflow.webhook.secret}") String secret,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(callbackBaseUrl).build();
        this.secret = secret;
        this.objectMapper = objectMapper;
    }

    public void sendAsync(String eventType, String chargeId, BigDecimal amount, String currency) {
        CompletableFuture.runAsync(() -> send(eventType, chargeId, amount, currency))
                .exceptionally(e -> {
                    log.warn("Failed to deliver webhook for {} {}", eventType, chargeId, e);
                    return null;
                });
    }

    private void send(String eventType, String chargeId, BigDecimal amount, String currency) {
        String eventId = "evt_mock_" + UUID.randomUUID().toString().replace("-", "");
        WebhookPayload payload = new WebhookPayload(eventId, eventType, chargeId, amount, currency, Instant.now());

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        long timestampSeconds = Instant.now().getEpochSecond();
        String signature = sign(timestampSeconds + "." + body);

        restClient.post()
                .uri("/v1/webhooks/providers/mock")
                .header("X-Mock-Signature", "t=" + timestampSeconds + ",v1=" + signature)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}
