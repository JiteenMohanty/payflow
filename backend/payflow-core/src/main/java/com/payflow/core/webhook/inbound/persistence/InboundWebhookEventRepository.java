package com.payflow.core.webhook.inbound.persistence;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.webhook.inbound.domain.InboundWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboundWebhookEventRepository extends JpaRepository<InboundWebhookEvent, UUID> {

    boolean existsByProviderCodeAndProviderEventId(ProviderCode providerCode, String providerEventId);
}
