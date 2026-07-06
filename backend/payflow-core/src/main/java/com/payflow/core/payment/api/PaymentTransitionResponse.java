package com.payflow.core.payment.api;

import com.payflow.core.payment.domain.PaymentActor;
import com.payflow.core.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentTransitionResponse(
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        PaymentActor actor,
        String reason,
        Instant createdAt
) {
}
