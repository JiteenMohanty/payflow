package com.payflow.core.webhook.outbound.domain;

public enum WebhookDeliveryStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    // Set only by the M9 scheduled retry job once attempt_number reaches
    // max_attempts (EDD section 7.5) - unreachable from any M8 code path,
    // which only ever performs the first attempt.
    EXHAUSTED
}
