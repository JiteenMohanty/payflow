package com.payflow.core.provider;

import java.math.BigDecimal;

/**
 * amount/currency are null when status is NOT_FOUND. Carried alongside
 * status (not just a bare status) so ReconciliationSweeper (M9, ADR-0011)
 * can reconcile a captured amount through the same
 * PaymentReconciliationSupport.reconcileCaptureConfirmation() path the
 * inbound webhook uses (M7), which also needs an amount - the provider's
 * async webhook is "the reconciliation source of truth" for that value, and
 * a status-only poll shouldn't be a lesser source of truth for it.
 */
public record ProviderChargeStatusResult(ProviderChargeStatus status, BigDecimal amount, String currency) {
}
