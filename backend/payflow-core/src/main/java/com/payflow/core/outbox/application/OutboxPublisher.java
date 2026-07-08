package com.payflow.core.outbox.application;

import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays PENDING outbox rows to Kafka - see ADR-0005 and EDD section 7.4.
 * relayPendingEvents() is a plain public method (not a private one only
 * reachable via @Scheduled) so tests can invoke it directly and
 * deterministically, without waiting on or racing the schedule - the test
 * profile sets a very long poll-interval-ms so the schedule itself never
 * fires during a test run.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    // Bounds the wait on the returned future only. KafkaTemplate.send()
    // itself blocks synchronously, before returning that future, while the
    // producer fetches initial cluster metadata - that block is bounded
    // separately by spring.kafka.producer.properties.max.block.ms
    // (application.yml), which must stay below this value for this
    // constant to be the effective per-attempt bound.
    private static final int SEND_TIMEOUT_SECONDS = 5;
    private static final int ERROR_REASON_MAX_LENGTH = 500;

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterWriter deadLetterWriter;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:500}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(properties.batchSize());
        for (OutboxEvent event : batch) {
            publishOne(event);
        }
    }

    // Never lets an exception escape - relayPendingEvents() is one
    // transaction for the whole batch, so an uncaught exception here would
    // roll back every row already marked PUBLISHED earlier in this same
    // cycle (harmless - they'd just be re-sent next cycle, and consumers
    // are required to be idempotent per ADR-0002 - but pointless churn).
    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getKafkaTopic(), event.getAggregateId().toString(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markPublished();
        } catch (Exception e) {
            event.incrementRetryCount();
            log.warn("Outbox publish failed for event {} (attempt {})", event.getId(), event.getRetryCount(), e);
            if (event.getRetryCount() >= properties.maxRetries()) {
                deadLetterWriter.write(event.getAggregateType(), event.getAggregateId(), event.getPayload(), describe(e));
                outboxEventRepository.delete(event);
            }
        }
    }

    /**
     * java.util.concurrent.TimeoutException from Future.get(timeout) - the
     * exact exception a Kafka outage produces here - has a null getMessage()
     * by construction, which would otherwise leave the one column
     * dead_letter_events exists to make operator-visible (per ADR-0005)
     * empty for the most common failure case.
     */
    private String describe(Exception e) {
        String message = e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName();
        return message.length() > ERROR_REASON_MAX_LENGTH ? message.substring(0, ERROR_REASON_MAX_LENGTH) : message;
    }
}
