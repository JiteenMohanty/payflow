package com.payflow.core.payment.api;

import java.util.List;

public record PaymentDetailResponse(PaymentResponse payment, List<PaymentTransitionResponse> transitions) {
}
