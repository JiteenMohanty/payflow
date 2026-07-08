package com.payflow.mockprovider.api;

import java.math.BigDecimal;

public record ChargeStatusResponse(String status, BigDecimal amount, String currency) {
}
