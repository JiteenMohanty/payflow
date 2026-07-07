package com.payflow.core.webhook.outbound.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.webhook.outbound.application.CreateWebhookEndpointResult;
import com.payflow.core.webhook.outbound.application.WebhookDeliverySummary;
import com.payflow.core.webhook.outbound.application.WebhookEndpointService;
import com.payflow.core.webhook.outbound.application.WebhookEndpointSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Merchant-facing, API-key authenticated only - same convention as
 * PaymentController (EDD section 5.1).
 */
@RestController
@RequestMapping("/v1/webhook-endpoints")
@RequiredArgsConstructor
public class WebhookEndpointController {

    private final WebhookEndpointService webhookEndpointService;

    @PostMapping
    public ResponseEntity<CreateWebhookEndpointResponse> create(@Valid @RequestBody CreateWebhookEndpointRequest request) {
        UUID organizationId = requireApiKeyOrganization();
        CreateWebhookEndpointResult result = webhookEndpointService.createEndpoint(
                organizationId, request.url(), request.subscribedEvents());
        return ResponseEntity.status(HttpStatus.CREATED).body(toCreateResponse(result));
    }

    @GetMapping
    public List<WebhookEndpointResponse> list() {
        UUID organizationId = requireApiKeyOrganization();
        return webhookEndpointService.listEndpoints(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> disable(@PathVariable UUID endpointId) {
        UUID organizationId = requireApiKeyOrganization();
        webhookEndpointService.disableEndpoint(organizationId, endpointId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{endpointId}/deliveries")
    public List<WebhookDeliveryResponse> deliveries(@PathVariable UUID endpointId) {
        UUID organizationId = requireApiKeyOrganization();
        return webhookEndpointService.listDeliveries(organizationId, endpointId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    private UUID requireApiKeyOrganization() {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.API_KEY) {
            throw new AccessDeniedException("This endpoint requires an API key");
        }
        return context.organizationId();
    }

    private CreateWebhookEndpointResponse toCreateResponse(CreateWebhookEndpointResult result) {
        WebhookEndpointSummary endpoint = result.endpoint();
        return new CreateWebhookEndpointResponse(
                endpoint.id(), endpoint.url(), endpoint.subscribedEvents(), endpoint.status(), endpoint.createdAt(), result.secret());
    }

    private WebhookEndpointResponse toResponse(WebhookEndpointSummary summary) {
        return new WebhookEndpointResponse(
                summary.id(), summary.url(), summary.subscribedEvents(), summary.status(), summary.createdAt());
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDeliverySummary summary) {
        return new WebhookDeliveryResponse(
                summary.id(), summary.eventType(), summary.attemptNumber(), summary.status(),
                summary.responseCode(), summary.nextRetryAt(), summary.createdAt());
    }
}
