package com.payflow.core.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Where an OutboxEvent lands once it has exceeded max retries - see
 * ADR-0005. Terminal: nothing in this module reads these rows back out for
 * further processing, they exist for operator visibility.
 */
@Entity
@Table(name = "dead_letter_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(nullable = false)
    private String payload;

    @Column(name = "error_reason", length = 500)
    private String errorReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DeadLetterEvent(String sourceType, UUID sourceId, String payload, String errorReason) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.payload = payload;
        this.errorReason = errorReason;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
