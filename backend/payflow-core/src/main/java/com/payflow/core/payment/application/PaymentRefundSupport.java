package com.payflow.core.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The only surface the refund module is allowed to use to read or mutate a
 * payment - refund depends on payment.application, never payment.domain or
 * payment.persistence (see EDD section 3). Implemented by PaymentService so
 * PaymentStateMachine validation and transition recording stay encapsulated
 * inside the payment module.
 */
public interface PaymentRefundSupport {

    /**
     * Throws DomainValidationException if the payment is not currently in a
     * refundable state (CAPTURED or PARTIALLY_REFUNDED), before the caller
     * has computed an amount or contacted a provider.
     */
    PaymentRefundContext loadForRefund(UUID organizationId, UUID paymentId);

    /**
     * Applies a successful refund's effect: increases refundedAmount,
     * transitions to PARTIALLY_REFUNDED or REFUNDED, and records the
     * transition. Must be called after the provider has already confirmed
     * the refund.
     */
    void applyRefund(UUID organizationId, UUID paymentId, BigDecimal refundAmount);
}
