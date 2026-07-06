package com.payflow.core.provider;

import java.math.BigDecimal;

public record ProviderCaptureRequest(String providerReference, BigDecimal amount, String currency) {
}
