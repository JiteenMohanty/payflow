package com.payflow.core.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String key;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyKeyStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyKey(UUID organizationId, String key, String requestFingerprint, Instant expiresAt) {
        this.organizationId = organizationId;
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.status = IdempotencyKeyStatus.IN_PROGRESS;
        this.expiresAt = expiresAt;
    }

    public void markCompleted(int statusCode, String responseBody) {
        this.responseStatusCode = statusCode;
        this.responseBody = responseBody;
        this.status = IdempotencyKeyStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
