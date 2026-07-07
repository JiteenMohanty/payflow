package com.payflow.core.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox payload for payment.* events - see EDD section 6. Lives in common
 * (which explicitly owns "event contracts" per section 3, and per that same
 * section must have zero dependency on any other module) since the webhook
 * module consumes these without depending on payment.domain. status is a
 * plain String, not payment.domain.PaymentStatus, for the same reason -
 * common cannot import from payment.
 *
 * eventType (e.g. "payment.captured") is carried in the payload itself, not
 * just the outbox_events.event_type column - WebhookDispatcher (M8) reads
 * this straight off the Kafka message body, and the message body is all it
 * has: it has no access to the outbox row the message was relayed from.
 */
public record PaymentEventPayload(
        String eventType,
        UUID paymentId,
        UUID organizationId,
        UUID merchantId,
        String status,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
