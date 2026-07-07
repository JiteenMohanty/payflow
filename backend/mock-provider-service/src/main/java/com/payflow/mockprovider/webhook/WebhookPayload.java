package com.payflow.mockprovider.webhook;

import java.math.BigDecimal;
import java.time.Instant;

public record WebhookPayload(
        String eventId,
        String eventType,
        String chargeId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
