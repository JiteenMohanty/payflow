package com.payflow.core.provider;

import java.math.BigDecimal;

public record ProviderAuthorizationRequest(BigDecimal amount, String currency, String merchantReference) {
}
