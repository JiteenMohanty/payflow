package com.payflow.core.payment.application;

import com.payflow.core.common.provider.ProviderCode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exactly what the refund module needs to compute and apply a refund,
 * without reaching into payment.domain - see PaymentRefundSupport.
 */
public record PaymentRefundContext(
        UUID paymentId,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String providerReference,
        ProviderCode providerCode
) {
}
