package com.payflow.core.webhook.outbound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * secretEncrypted mirrors provider_accounts.credentials_encrypted (AES-GCM
 * envelope encryption via SymmetricEncryptor) - the plaintext secret is
 * shown to the merchant exactly once, at creation, and never stored or
 * returned again (EDD section 5.1).
 */
@Entity
@Table(name = "webhook_endpoints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "secret_encrypted", nullable = false)
    private byte[] secretEncrypted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subscribed_events", nullable = false, columnDefinition = "jsonb")
    private List<String> subscribedEvents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEndpointStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WebhookEndpoint(UUID organizationId, String url, byte[] secretEncrypted, List<String> subscribedEvents) {
        this.organizationId = organizationId;
        this.url = url;
        this.secretEncrypted = secretEncrypted;
        this.subscribedEvents = subscribedEvents;
        this.status = WebhookEndpointStatus.ACTIVE;
    }

    public void disable() {
        this.status = WebhookEndpointStatus.DISABLED;
    }

    public boolean isSubscribedTo(String eventType) {
        return status == WebhookEndpointStatus.ACTIVE && subscribedEvents.contains(eventType);
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
