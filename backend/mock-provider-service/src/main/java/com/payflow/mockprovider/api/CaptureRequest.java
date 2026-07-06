package com.payflow.mockprovider.api;

import java.math.BigDecimal;

public record CaptureRequest(BigDecimal amount, String currency) {
}
