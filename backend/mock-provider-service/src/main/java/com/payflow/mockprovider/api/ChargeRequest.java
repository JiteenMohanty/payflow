package com.payflow.mockprovider.api;

import java.math.BigDecimal;

public record ChargeRequest(BigDecimal amount, String currency, String merchantReference) {
}
