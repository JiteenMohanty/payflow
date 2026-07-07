package com.payflow.core.ledger.domain;

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
 * The journal entry header - one per capture, refund, or adjustment. Plain
 * organizationId/paymentId values, not JPA relationships: ledger depends
 * only on common (see EDD section 3), it must not reach into payment's
 * persistence layer.
 */
@Entity
@Table(name = "ledger_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private LedgerTransactionType transactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerTransaction(UUID organizationId, UUID paymentId, LedgerTransactionType transactionType) {
        this.organizationId = organizationId;
        this.paymentId = paymentId;
        this.transactionType = transactionType;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
