package com.payflow.core.payment.application;

import java.util.List;

public record PaymentDetail(PaymentSummary payment, List<PaymentTransitionSummary> transitions) {
}
