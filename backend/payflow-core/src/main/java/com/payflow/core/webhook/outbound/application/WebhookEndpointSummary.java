package com.payflow.core.webhook.outbound.application;

import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointSummary(
        UUID id,
        String url,
        List<String> subscribedEvents,
        WebhookEndpointStatus status,
        Instant createdAt
) {
}
