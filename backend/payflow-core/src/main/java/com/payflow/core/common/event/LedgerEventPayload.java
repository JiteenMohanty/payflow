package com.payflow.core.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox payload for ledger.transaction_recorded - see EDD section 6.
 * Emitted by payment/refund right after calling LedgerService, since the
 * ledger module itself depends only on common (EDD section 3) and must
 * never depend on outbox.
 */
public record LedgerEventPayload(
        UUID organizationId,
        UUID paymentId,
        String transactionType,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
