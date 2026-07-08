package com.payflow.core.dashboard.api;

import com.payflow.core.payment.domain.PaymentActor;
import com.payflow.core.payment.domain.PaymentStatus;

import java.time.Instant;

public record DashboardPaymentTransitionResponse(
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        PaymentActor actor,
        String reason,
        Instant createdAt
) {
}
