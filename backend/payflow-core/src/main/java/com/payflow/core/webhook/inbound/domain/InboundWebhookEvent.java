package com.payflow.core.webhook.inbound.domain;

import com.payflow.core.common.provider.ProviderCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A verified, deduplicated record of an inbound provider webhook - see
 * ADR-0011. Unverified (invalid-signature) attempts are rejected before
 * ever reaching this table, so signatureValid is always true for any row
 * that exists; the column stays because it's still a correct, meaningful
 * fact about the row, not because either value is currently reachable.
 */
@Entity
@Table(name = "inbound_webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 20)
    private ProviderCode providerCode;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private InboundWebhookProcessingStatus processingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InboundWebhookEvent(ProviderCode providerCode, String providerEventId, String eventType,
                                String payload, boolean signatureValid, InboundWebhookProcessingStatus processingStatus) {
        this.providerCode = providerCode;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.signatureValid = signatureValid;
        this.processingStatus = processingStatus;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
