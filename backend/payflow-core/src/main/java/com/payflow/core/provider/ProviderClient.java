package com.payflow.core.provider;

import com.payflow.core.common.provider.ProviderCode;

public interface ProviderClient {

    ProviderCode providerCode();

    ProviderAuthorizationResult authorize(ProviderAuthorizationRequest request);

    ProviderCaptureResult capture(ProviderCaptureRequest request);

    ProviderRefundResult refund(ProviderRefundRequest request);

    /**
     * Polled by ReconciliationSweeper (M9, ADR-0011) as a backstop for a
     * lost webhook - the webhook remains the primary, faster reconciliation
     * path (M7); this exists for payments stuck too long without one.
     */
    ProviderChargeStatusResult checkStatus(String providerReference);
}
