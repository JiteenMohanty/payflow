package com.payflow.core.payment.application;

import com.payflow.core.common.provider.ProviderCode;

import java.math.BigDecimal;

/**
 * The only surface the webhook module is allowed to use to reconcile
 * payment state from an inbound provider webhook - webhook depends on
 * payment.application only, never payment.domain or payment.persistence
 * (see EDD section 3, ADR-0011). Implemented by PaymentService so
 * PaymentStateMachine validation, ledger posting, and outbox emission stay
 * identical to the synchronous path and encapsulated inside the payment
 * module.
 *
 * Only capture confirmation is reconciled for M7 - see PaymentService's
 * class-level note for why authorize/refund reconciliation is out of scope.
 */
public interface PaymentReconciliationSupport {

    /**
     * Idempotent: a no-op if the payment is already CAPTURED (or beyond),
     * not found, or the amount exceeds what the payment could still
     * legally capture. Safe to call repeatedly for the same event.
     */
    void reconcileCaptureConfirmation(ProviderCode providerCode, String providerReference, BigDecimal amount, String currency);
}
