package com.payflow.core.payment.application;

import com.payflow.core.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSummary(
        UUID id,
        UUID merchantId,
        String providerReference,
        BigDecimal amount,
        String currency,
        String description,
        PaymentStatus status,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        Instant createdAt,
        Instant authorizedAt,
        Instant capturedAt
) {
}
