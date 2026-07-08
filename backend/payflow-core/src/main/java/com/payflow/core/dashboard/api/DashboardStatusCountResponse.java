package com.payflow.core.dashboard.api;

import com.payflow.core.payment.domain.PaymentStatus;

import java.math.BigDecimal;

public record DashboardStatusCountResponse(PaymentStatus status, long count, BigDecimal totalAmount) {
}
