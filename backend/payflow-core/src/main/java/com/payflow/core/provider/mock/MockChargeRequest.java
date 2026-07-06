package com.payflow.core.provider.mock;

import java.math.BigDecimal;

public record MockChargeRequest(BigDecimal amount, String currency, String merchantReference) {
}
