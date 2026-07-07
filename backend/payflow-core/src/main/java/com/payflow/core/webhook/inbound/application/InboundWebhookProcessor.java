package com.payflow.core.webhook.inbound.application;

import com.payflow.core.common.provider.ProviderCode;

/**
 * Verifies, deduplicates, and reconciles an inbound provider webhook - see
 * ADR-0011 and EDD section 5.4. rawBody is verified against exactly the
 * bytes the provider signed, before any JSON parsing - the controller must
 * pass the untouched request body, not a re-serialized DTO.
 */
public interface InboundWebhookProcessor {

    void process(ProviderCode providerCode, String rawBody, String signatureHeader);
}
