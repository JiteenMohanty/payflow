package com.payflow.core.ledger.application;

import com.payflow.core.ledger.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntrySummary(
        UUID id,
        UUID ledgerTransactionId,
        UUID paymentId,
        String accountCode,
        LedgerEntryType entryType,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}
