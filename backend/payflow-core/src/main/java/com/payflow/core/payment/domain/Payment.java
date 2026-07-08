package com.payflow.core.payment.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * organizationId and merchantId are plain values, not JPA relationships -
 * cross-module references go through the organization/merchant modules'
 * application-layer services, never their persistence layers.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "provider_account_id")
    private UUID providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", length = 20)
    private ProviderCode providerCode;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "captured_amount", nullable = false)
    private BigDecimal capturedAmount;

    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    public Payment(UUID organizationId, UUID merchantId, BigDecimal amount, String currency,
                   String description, Map<String, Object> metadata) {
        this.organizationId = organizationId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.metadata = metadata;
        this.status = PaymentStatus.CREATED;
        this.capturedAmount = BigDecimal.ZERO;
        this.refundedAmount = BigDecimal.ZERO;
    }

    public BigDecimal remainingCapturableAmount() {
        return amount.subtract(capturedAmount);
    }

    public void markAuthorized(UUID providerAccountId, ProviderCode providerCode, String providerReference) {
        PaymentStateMachine.validateTransition(status, PaymentStatus.AUTHORIZED);
        this.providerAccountId = providerAccountId;
        this.providerCode = providerCode;
        this.providerReference = providerReference;
        this.status = PaymentStatus.AUTHORIZED;
        this.authorizedAt = Instant.now();
    }

    public void markAuthorizationFailed() {
        PaymentStateMachine.validateTransition(status, PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }

    public void markCaptured(BigDecimal captureAmount) {
        PaymentStateMachine.validateTransition(status, PaymentStatus.CAPTURED);
        this.capturedAmount = captureAmount;
        this.status = PaymentStatus.CAPTURED;
        this.capturedAt = Instant.now();
    }

    public void applyRefund(BigDecimal refundAmount, PaymentStatus targetStatus) {
        PaymentStateMachine.validateTransition(status, targetStatus);
        this.refundedAmount = this.refundedAmount.add(refundAmount);
        this.status = targetStatus;
    }

    public void markExpired() {
        PaymentStateMachine.validateTransition(status, PaymentStatus.EXPIRED);
        this.status = PaymentStatus.EXPIRED;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
