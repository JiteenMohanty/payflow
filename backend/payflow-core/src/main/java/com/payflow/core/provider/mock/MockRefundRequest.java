package com.payflow.core.provider.mock;

import java.math.BigDecimal;

public record MockRefundRequest(BigDecimal amount, String currency) {
}
