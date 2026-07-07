package com.payflow.core.webhook.inbound.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The inbound provider webhook body shape - see EDD section 5.4. Provider-
 * agnostic by design (chargeId/eventType/eventId are generic terms every
 * provider adapter maps its own webhook shape onto), matching how
 * ProviderClient abstracts each provider's own API shape today. Parsed
 * internally by InboundWebhookService from the controller's raw body - the
 * controller itself only ever handles the untyped String (matching how
 * PaymentController passes primitives to PaymentService rather than the
 * application layer depending on api-layer request DTOs).
 */
public record ProviderWebhookPayload(
        String eventId,
        String eventType,
        String chargeId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
