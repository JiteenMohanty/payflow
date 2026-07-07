package com.payflow.mockprovider.api;

import java.math.BigDecimal;

public record RefundRequest(BigDecimal amount, String currency) {
}
