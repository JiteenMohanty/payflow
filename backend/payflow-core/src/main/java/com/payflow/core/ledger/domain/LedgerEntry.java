package com.payflow.core.ledger.domain;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only: no setters beyond construction, and the database itself
 * rejects UPDATE/DELETE on this table regardless of role (see
 * V5__ledger.sql) - corrections are always a new, reversing entry under a
 * new LedgerTransaction, never an edit to an existing row.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_transaction_id", nullable = false)
    private LedgerTransaction ledgerTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false)
    private LedgerAccount ledgerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry(LedgerTransaction ledgerTransaction, LedgerAccount ledgerAccount,
                        LedgerEntryType entryType, BigDecimal amount, String currency) {
        this.ledgerTransaction = ledgerTransaction;
        this.ledgerAccount = ledgerAccount;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
