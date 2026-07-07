package com.payflow.core.refund.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * organizationId and paymentId are plain values, not JPA relationships -
 * refund depends on payment.application only, never payment.persistence
 * (see EDD section 3). Resolved synchronously in one call (unlike Payment,
 * which has a multi-step lifecycle): the provider has already confirmed the
 * refund by the time this is constructed, so there is no separate
 * "mark succeeded" step - a FAILED provider response is never persisted at
 * all (see RefundService), matching how PaymentService.capture() handles a
 * failed capture.
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(length = 255)
    private String reason;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Refund(UUID organizationId, UUID paymentId, BigDecimal amount, String currency,
                  RefundStatus status, String reason, String providerReference) {
        this.organizationId = organizationId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
        this.providerReference = providerReference;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
