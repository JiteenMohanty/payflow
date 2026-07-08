package com.payflow.core.payment.application;

import com.payflow.core.payment.domain.PaymentStatus;

import java.math.BigDecimal;

public record DashboardStatusCount(PaymentStatus status, long count, BigDecimal totalAmount) {
}
