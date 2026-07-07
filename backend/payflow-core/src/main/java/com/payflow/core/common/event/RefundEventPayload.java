package com.payflow.core.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox payload for refund.* events - see EDD section 6. eventType is
 * carried in the payload itself - see PaymentEventPayload's note on why.
 */
public record RefundEventPayload(
        String eventType,
        UUID refundId,
        UUID paymentId,
        UUID organizationId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
