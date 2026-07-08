package com.payflow.core.dashboard.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * amount is optional, mirroring CreateRefundRequest (refund.api) - omitting
 * it refunds the remaining captured balance in full.
 */
public record CreateDashboardRefundRequest(
        @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 255) String reason
) {
}
