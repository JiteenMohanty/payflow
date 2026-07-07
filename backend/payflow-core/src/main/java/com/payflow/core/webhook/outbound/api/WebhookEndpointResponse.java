package com.payflow.core.webhook.outbound.api;

import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
        UUID id,
        String url,
        List<String> subscribedEvents,
        WebhookEndpointStatus status,
        Instant createdAt
) {
}
