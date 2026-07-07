package com.payflow.core.webhook.outbound.api;

import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        String eventType,
        int attemptNumber,
        WebhookDeliveryStatus status,
        Integer responseCode,
        Instant nextRetryAt,
        Instant createdAt
) {
}
