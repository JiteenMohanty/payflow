package com.payflow.core.webhook.outbound.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.infrastructure.web.CorrelationIdFilter;
import com.payflow.core.webhook.outbound.domain.WebhookBackoff;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
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
    private final MeterRegistry meterRegistry;

    public WebhookDispatcher(
            WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttempter deliveryAttempter, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttempter = deliveryAttempter;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = {OutboxTopics.PAYMENTS, OutboxTopics.REFUNDS}, groupId = "webhook-dispatcher")
    public void onMessage(
            String rawPayload,
            @Header(name = CorrelationIdFilter.HEADER_NAME, required = false) String correlationId) {
        // Restores the id OutboxPublisher forwarded as a Kafka header
        // (ADR-0012) so this consume-and-deliver's own logs correlate with
        // that publish cycle - falls back to a fresh id only for a message
        // that somehow arrives without one (there shouldn't be any once
        // OutboxPublisher always sets it, but onMessage must not NPE on an
        // absent header).
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId != null ? correlationId : UUID.randomUUID().toString());
        try {
            EventEnvelope envelope = parseEnvelope(rawPayload);
            if (envelope == null) {
                log.warn("Could not parse event envelope from webhook dispatch message, skipping: {}", rawPayload);
                return;
            }

            endpointRepository.findByOrganizationIdAndStatus(envelope.organizationId(), WebhookEndpointStatus.ACTIVE).stream()
                    .filter(endpoint -> endpoint.isSubscribedTo(envelope.eventType()))
                    .forEach(endpoint -> deliver(endpoint, envelope.eventType(), rawPayload));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void deliver(WebhookEndpoint endpoint, String eventType, String payload) {
        WebhookDelivery delivery = deliveryRepository.save(new WebhookDelivery(endpoint, eventType, payload));

        DeliveryOutcome outcome = deliveryAttempter.attempt(endpoint, payload);
        if (outcome.succeeded()) {
            delivery.markSucceeded(outcome.responseCode());
            meterRegistry.counter(DeliveryOutcome.DELIVERIES_METRIC, "outcome", DeliveryOutcome.OUTCOME_SUCCEEDED).increment();
        } else {
            delivery.markFailed(outcome.responseCode(), WebhookBackoff.nextRetryAt(delivery.getAttemptNumber()));
            meterRegistry.counter(DeliveryOutcome.DELIVERIES_METRIC, "outcome", DeliveryOutcome.OUTCOME_FAILED).increment();
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
