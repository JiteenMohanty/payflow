package com.payflow.core.webhook.outbound.scheduled;

import com.payflow.core.infrastructure.web.ScheduledJobCorrelation;
import com.payflow.core.outbox.application.DeadLetterWriter;
import com.payflow.core.webhook.outbound.application.DeliveryOutcome;
import com.payflow.core.webhook.outbound.application.WebhookDeliveryAttempter;
import com.payflow.core.webhook.outbound.domain.WebhookBackoff;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "select FAILED where next_retry_at &lt;= now, retry, on success mark
 * SUCCEEDED, on failure attempt += 1 and reschedule, once attempt == max
 * mark EXHAUSTED and insert dead_letter_events" - EDD section 7.5's
 * retry/DLQ sequence, implemented exactly. Reuses WebhookDeliveryAttempter,
 * the same sign+POST+interpret logic WebhookDispatcher's first attempt uses
 * (M8), so a retried delivery is indistinguishable, on the wire, from the
 * original attempt.
 */
@Component
@RequiredArgsConstructor
public class WebhookRetryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryJob.class);
    private static final String DEAD_LETTER_SOURCE_TYPE = "webhook_delivery";

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttempter deliveryAttempter;
    private final DeadLetterWriter deadLetterWriter;
    private final WebhookRetryProperties properties;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${payflow.webhook-retry.poll-interval-ms:30000}")
    @Transactional
    public void retryFailedDeliveries() {
        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> {
            List<WebhookDelivery> batch = deliveryRepository.lockNextRetryBatch(properties.batchSize());
            for (WebhookDelivery delivery : batch) {
                retryOne(delivery);
            }
        });
    }

    // Never lets an exception escape - retryFailedDeliveries() is one
    // transaction for the whole batch, same reasoning as
    // OutboxPublisher.publishOne().
    private void retryOne(WebhookDelivery delivery) {
        try {
            WebhookEndpoint endpoint = delivery.getWebhookEndpoint();
            if (endpoint.getStatus() != WebhookEndpointStatus.ACTIVE) {
                delivery.pauseRetry();
                return;
            }

            DeliveryOutcome outcome = deliveryAttempter.attempt(endpoint, delivery.getPayload());
            if (outcome.succeeded()) {
                delivery.markSucceeded(outcome.responseCode());
                meterRegistry.counter(DeliveryOutcome.DELIVERIES_METRIC, "outcome", DeliveryOutcome.OUTCOME_SUCCEEDED).increment();
                return;
            }

            int nextAttemptNumber = delivery.getAttemptNumber() + 1;
            if (nextAttemptNumber >= properties.maxAttempts()) {
                delivery.markExhausted(outcome.responseCode());
                deadLetterWriter.write(DEAD_LETTER_SOURCE_TYPE, delivery.getId(), delivery.getPayload(), describe(outcome));
                meterRegistry.counter(DeliveryOutcome.DELIVERIES_METRIC, "outcome", DeliveryOutcome.OUTCOME_EXHAUSTED).increment();
            } else {
                delivery.incrementAttemptAndScheduleRetry(outcome.responseCode(), WebhookBackoff.nextRetryAt(nextAttemptNumber));
                meterRegistry.counter(DeliveryOutcome.DELIVERIES_METRIC, "outcome", DeliveryOutcome.OUTCOME_FAILED).increment();
            }
        } catch (Exception e) {
            log.warn("Webhook retry failed for delivery {}", delivery.getId(), e);
        }
    }

    private String describe(DeliveryOutcome outcome) {
        return outcome.responseCode() != null
                ? "HTTP " + outcome.responseCode()
                : "No response (connection/timeout failure)";
    }
}
