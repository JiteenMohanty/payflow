package com.payflow.core.dashboard.api;

import com.payflow.core.refund.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardRefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String reason,
        String providerReference,
        Instant createdAt
) {
}
