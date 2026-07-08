package com.payflow.core.dashboard.api;

import com.payflow.core.ledger.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardLedgerEntryResponse(
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
