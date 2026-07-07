package com.payflow.core.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox payload for payment.* events - see EDD section 6. Lives in common
 * (which explicitly owns "event contracts" per section 3, and per that same
 * section must have zero dependency on any other module) since the future
 * webhook module will consume these without depending on payment.domain.
 * status is a plain String, not payment.domain.PaymentStatus, for the same
 * reason - common cannot import from payment.
 */
public record PaymentEventPayload(
        UUID paymentId,
        UUID organizationId,
        UUID merchantId,
        String status,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
