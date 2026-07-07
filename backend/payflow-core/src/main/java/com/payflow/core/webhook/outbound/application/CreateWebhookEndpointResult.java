package com.payflow.core.webhook.outbound.application;

/**
 * secret is shown here once - it is never returned again after creation
 * (EDD section 5.1), matching the same convention as API key issuance.
 */
public record CreateWebhookEndpointResult(WebhookEndpointSummary endpoint, String secret) {
}
