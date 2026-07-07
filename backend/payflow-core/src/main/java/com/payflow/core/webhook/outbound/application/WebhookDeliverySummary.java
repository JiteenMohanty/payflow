package com.payflow.core.webhook.outbound.application;

import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliverySummary(
        UUID id,
        String eventType,
        int attemptNumber,
        WebhookDeliveryStatus status,
        Integer responseCode,
        Instant nextRetryAt,
        Instant createdAt
) {
}
