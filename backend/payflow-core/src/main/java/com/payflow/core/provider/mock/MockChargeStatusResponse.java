package com.payflow.core.provider.mock;

import java.math.BigDecimal;

public record MockChargeStatusResponse(String status, BigDecimal amount, String currency) {
}
