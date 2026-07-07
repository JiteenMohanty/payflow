package com.payflow.core.refund.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * amount is optional - omitting it refunds the remaining captured balance in
 * full (see EDD section 5.1). @DecimalMin only fires when a value is
 * present, so it does not conflict with that.
 */
public record CreateRefundRequest(
        @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 255) String reason
) {
}
