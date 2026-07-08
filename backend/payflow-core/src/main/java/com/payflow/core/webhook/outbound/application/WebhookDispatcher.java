package com.payflow.core.webhook.outbound.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.webhook.outbound.domain.WebhookBackoff;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer feeding merchant webhook delivery from the outbox - see
 * EDD section 7.4/7.5. Performs only the first delivery attempt, via the
 * shared WebhookDeliveryAttempter; a FAILED row just sits with its computed
 * next_retry_at until WebhookRetryJob (M9) picks it up.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttempter deliveryAttempter;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(
            WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttempter deliveryAttempter, ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttempter = deliveryAttempter;
        this.objectMapper = objectMapper;
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

        DeliveryOutcome outcome = deliveryAttempter.attempt(endpoint, payload);
        if (outcome.succeeded()) {
            delivery.markSucceeded(outcome.responseCode());
        } else {
            delivery.markFailed(outcome.responseCode(), WebhookBackoff.nextRetryAt(delivery.getAttemptNumber()));
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

    private record EventEnvelope(String eventType, UUID organizationId) {
    }
}
