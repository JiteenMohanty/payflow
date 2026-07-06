package com.payflow.core.provider.mock;

import java.math.BigDecimal;

public record MockCaptureRequest(BigDecimal amount, String currency) {
}
