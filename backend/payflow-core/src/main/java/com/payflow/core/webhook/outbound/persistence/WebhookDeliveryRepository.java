package com.payflow.core.webhook.outbound.persistence;

import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByWebhookEndpointIdOrderByCreatedAtDesc(UUID webhookEndpointId);

    /**
     * Used by WebhookRetryJob (M9). FOR UPDATE SKIP LOCKED for the same
     * reason as OutboxEventRepository.lockNextBatch - lets multiple app
     * instances retry concurrently without redelivering the same webhook to
     * a merchant twice in the same cycle.
     */
    @Query(value = "SELECT * FROM webhook_deliveries WHERE status = 'FAILED' AND next_retry_at IS NOT NULL "
            + "AND next_retry_at <= now() ORDER BY next_retry_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<WebhookDelivery> lockNextRetryBatch(int limit);
}
