package com.payflow.core.payment.api;

import java.math.BigDecimal;

public record CapturePaymentRequest(BigDecimal amount) {
}
