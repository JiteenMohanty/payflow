package com.payflow.core.payment.application;

import com.payflow.core.payment.domain.PaymentActor;
import com.payflow.core.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentTransitionSummary(
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        PaymentActor actor,
        String reason,
        Instant createdAt
) {
}
