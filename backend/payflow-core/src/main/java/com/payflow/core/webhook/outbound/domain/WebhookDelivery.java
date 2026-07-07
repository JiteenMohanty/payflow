package com.payflow.core.webhook.outbound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per delivery attempt sequence for one event to one endpoint -
 * inserted PENDING before the HTTP attempt, updated to a terminal status
 * after (EDD section 7.5), not written once with a final status like
 * Refund - WebhookRetryJob (M9) is designed to resume a FAILED row by its
 * own next_retry_at, so PENDING is a real, load-bearing intermediate state
 * here, not an implementation detail.
 */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_endpoint_id", nullable = false)
    private WebhookEndpoint webhookEndpoint;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WebhookDelivery(WebhookEndpoint webhookEndpoint, String eventType, String payload) {
        this.webhookEndpoint = webhookEndpoint;
        this.eventType = eventType;
        this.payload = payload;
        this.attemptNumber = 1;
        this.status = WebhookDeliveryStatus.PENDING;
    }

    public void markSucceeded(int responseCode) {
        this.status = WebhookDeliveryStatus.SUCCEEDED;
        this.responseCode = responseCode;
    }

    public void markFailed(Integer responseCode, Instant nextRetryAt) {
        this.status = WebhookDeliveryStatus.FAILED;
        this.responseCode = responseCode;
        this.nextRetryAt = nextRetryAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
