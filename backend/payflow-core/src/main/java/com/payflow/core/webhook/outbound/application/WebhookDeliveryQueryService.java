package com.payflow.core.webhook.outbound.application;

import java.util.List;
import java.util.UUID;

/**
 * The read-only surface the dashboard module (M12) depends on for webhook
 * history, kept separate from WebhookEndpointService's own mutation methods
 * (register/disable) - named in the EDD's module table since Phase 0 but
 * never actually extracted until now, matching PaymentQueryService's own
 * reasoning for the same milestone.
 */
public interface WebhookDeliveryQueryService {

    List<WebhookEndpointSummary> listEndpoints(UUID organizationId);

    List<WebhookDeliverySummary> listDeliveries(UUID organizationId, UUID endpointId);
}
