package com.payflow.core.webhook.outbound.application;

/**
 * responseCode is null when the attempt never got an HTTP response at all
 * (connection failure, timeout) - distinct from a non-2xx response, which
 * carries the code the merchant endpoint actually returned.
 */
public record DeliveryOutcome(boolean succeeded, Integer responseCode) {

    /**
     * Shared with WebhookRetryJob (M9), which reports the same outcome
     * vocabulary for the same underlying metric (ADR-0012's "webhook
     * delivery success/failure/DLQ rate") - centralized here, the type both
     * WebhookDispatcher and WebhookRetryJob already share, rather than
     * risking two independently-typed literals drifting into two different
     * Prometheus metric names.
     */
    public static final String DELIVERIES_METRIC = "payflow.webhook.deliveries";
    public static final String OUTCOME_SUCCEEDED = "succeeded";
    public static final String OUTCOME_FAILED = "failed";
    public static final String OUTCOME_EXHAUSTED = "exhausted";
}
