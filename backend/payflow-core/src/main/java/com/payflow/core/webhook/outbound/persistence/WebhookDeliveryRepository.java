package com.payflow.core.webhook.outbound.persistence;

import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByWebhookEndpointIdOrderByCreatedAtDesc(UUID webhookEndpointId);
}
