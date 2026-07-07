package com.payflow.core.webhook.outbound.persistence;

import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<WebhookEndpoint> findByOrganizationId(UUID organizationId);

    List<WebhookEndpoint> findByOrganizationIdAndStatus(UUID organizationId, WebhookEndpointStatus status);
}
