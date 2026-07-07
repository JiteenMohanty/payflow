package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEndpointService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String SECRET_PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookUrlValidator urlValidator;
    private final SymmetricEncryptor encryptor;

    @Transactional
    public CreateWebhookEndpointResult createEndpoint(UUID organizationId, String url, List<String> subscribedEvents) {
        urlValidator.validate(url);
        if (subscribedEvents == null || subscribedEvents.isEmpty()) {
            throw new DomainValidationException("At least one subscribed event is required");
        }

        String secret = generateSecret();
        byte[] encryptedSecret = encryptor.encrypt(secret.getBytes(StandardCharsets.UTF_8));

        WebhookEndpoint endpoint = new WebhookEndpoint(organizationId, url, encryptedSecret, subscribedEvents);
        endpointRepository.save(endpoint);

        return new CreateWebhookEndpointResult(toSummary(endpoint), secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointSummary> listEndpoints(UUID organizationId) {
        return endpointRepository.findByOrganizationId(organizationId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void disableEndpoint(UUID organizationId, UUID endpointId) {
        loadOwnedEndpoint(organizationId, endpointId).disable();
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliverySummary> listDeliveries(UUID organizationId, UUID endpointId) {
        loadOwnedEndpoint(organizationId, endpointId);
        return deliveryRepository.findByWebhookEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                .map(this::toDeliverySummary)
                .toList();
    }

    private WebhookEndpoint loadOwnedEndpoint(UUID organizationId, UUID endpointId) {
        return endpointRepository.findByIdAndOrganizationId(endpointId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private WebhookEndpointSummary toSummary(WebhookEndpoint endpoint) {
        return new WebhookEndpointSummary(
                endpoint.getId(), endpoint.getUrl(), endpoint.getSubscribedEvents(), endpoint.getStatus(), endpoint.getCreatedAt());
    }

    private WebhookDeliverySummary toDeliverySummary(WebhookDelivery delivery) {
        return new WebhookDeliverySummary(
                delivery.getId(), delivery.getEventType(), delivery.getAttemptNumber(), delivery.getStatus(),
                delivery.getResponseCode(), delivery.getNextRetryAt(), delivery.getCreatedAt());
    }
}
