package com.payflow.core.provider;

import java.math.BigDecimal;

public record ProviderRefundRequest(String providerReference, BigDecimal amount, String currency) {
}
