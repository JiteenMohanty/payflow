package com.payflow.core.webhook.outbound.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.security.hmac.HmacSigner;
import com.payflow.core.webhook.outbound.domain.WebhookBackoff;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Kafka consumer feeding merchant webhook delivery from the outbox - see
 * EDD section 7.4/7.5. Performs only the first delivery attempt; a FAILED
 * row just sits with its computed next_retry_at until WebhookRetryJob (M9)
 * picks it up - no scheduled retry execution exists yet, only the schedule
 * itself.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final String SIGNATURE_HEADER = "X-PayFlow-Signature";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final SymmetricEncryptor encryptor;
    private final HmacSigner hmacSigner;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WebhookDispatcher(
            WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository,
            SymmetricEncryptor encryptor, HmacSigner hmacSigner, ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.encryptor = encryptor;
        this.hmacSigner = hmacSigner;
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient();
    }

    @KafkaListener(topics = {OutboxTopics.PAYMENTS, OutboxTopics.REFUNDS}, groupId = "webhook-dispatcher")
    public void onMessage(String rawPayload) {
        EventEnvelope envelope = parseEnvelope(rawPayload);
        if (envelope == null) {
            log.warn("Could not parse event envelope from webhook dispatch message, skipping: {}", rawPayload);
            return;
        }

        endpointRepository.findByOrganizationIdAndStatus(envelope.organizationId(), WebhookEndpointStatus.ACTIVE).stream()
                .filter(endpoint -> endpoint.isSubscribedTo(envelope.eventType()))
                .forEach(endpoint -> deliver(endpoint, envelope.eventType(), rawPayload));
    }

    private void deliver(WebhookEndpoint endpoint, String eventType, String payload) {
        WebhookDelivery delivery = deliveryRepository.save(new WebhookDelivery(endpoint, eventType, payload));

        String secret = new String(encryptor.decrypt(endpoint.getSecretEncrypted()), StandardCharsets.UTF_8);
        long timestampSeconds = System.currentTimeMillis() / 1000;
        String digest = hmacSigner.sign(secret, timestampSeconds + "." + payload);
        String signatureHeader = "t=" + timestampSeconds + ",v1=" + digest;

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(endpoint.getUrl())
                    .header(SIGNATURE_HEADER, signatureHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            // A 3xx never reaches here as a thrown exception - redirects are
            // never followed (see buildRestClient) - so it must be checked
            // explicitly rather than treating "no exception" as success.
            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.markSucceeded(response.getStatusCode().value());
            } else {
                delivery.markFailed(response.getStatusCode().value(), WebhookBackoff.nextRetryAt(delivery.getAttemptNumber()));
            }
        } catch (RestClientResponseException e) {
            delivery.markFailed(e.getStatusCode().value(), WebhookBackoff.nextRetryAt(delivery.getAttemptNumber()));
        } catch (Exception e) {
            log.warn("Webhook delivery failed for endpoint {}", endpoint.getId(), e);
            delivery.markFailed(null, WebhookBackoff.nextRetryAt(delivery.getAttemptNumber()));
        }
        deliveryRepository.save(delivery);
    }

    private EventEnvelope parseEnvelope(String rawPayload) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            String eventType = node.path("eventType").asText(null);
            String organizationIdText = node.path("organizationId").asText(null);
            if (eventType == null || organizationIdText == null) {
                return null;
            }
            return new EventEnvelope(eventType, UUID.fromString(organizationIdText));
        } catch (Exception e) {
            log.warn("Failed to parse webhook dispatch event envelope", e);
            return null;
        }
    }

    // Never follows redirects (EDD risk table: "outbound HTTP client denies
    // redirects to private ranges") - denying all redirects is a stricter
    // superset that doesn't need to inspect where a redirect points, since
    // it's never followed at all. Bounded connect/read timeouts so one slow
    // or hanging merchant endpoint can't tie up the Kafka consumer thread
    // indefinitely.
    private RestClient buildRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private record EventEnvelope(String eventType, UUID organizationId) {
    }
}
