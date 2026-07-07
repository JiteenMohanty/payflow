package com.payflow.core.ledger.application;

import com.payflow.core.ledger.domain.LedgerAccount;
import com.payflow.core.ledger.domain.LedgerAccountCode;
import com.payflow.core.ledger.domain.LedgerEntry;
import com.payflow.core.ledger.domain.LedgerEntryType;
import com.payflow.core.ledger.domain.LedgerTransaction;
import com.payflow.core.ledger.domain.LedgerTransactionType;
import com.payflow.core.ledger.persistence.LedgerAccountRepository;
import com.payflow.core.ledger.persistence.LedgerEntryRepository;
import com.payflow.core.ledger.persistence.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService, LedgerQueryService {

    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 200;

    private final LedgerAccountRepository accountRepository;
    private final LedgerAccountWriter accountWriter;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @Override
    @Transactional
    public void postCapture(UUID organizationId, UUID paymentId, BigDecimal amount, String currency) {
        LedgerAccount receivable = findOrCreateAccount(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE);
        LedgerAccount payable = findOrCreateAccount(organizationId, LedgerAccountCode.MERCHANT_PAYABLE);

        LedgerTransaction transaction = new LedgerTransaction(organizationId, paymentId, null, LedgerTransactionType.CAPTURE);
        transactionRepository.save(transaction);

        // Dr Provider Settlement Receivable / Cr Merchant Payable - the
        // deferred balance trigger validates both legs at commit time.
        entryRepository.save(new LedgerEntry(transaction, receivable, LedgerEntryType.DEBIT, amount, currency));
        entryRepository.save(new LedgerEntry(transaction, payable, LedgerEntryType.CREDIT, amount, currency));
    }

    @Override
    @Transactional
    public void postRefund(UUID organizationId, UUID paymentId, UUID refundId, BigDecimal amount, String currency) {
        LedgerAccount receivable = findOrCreateAccount(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE);
        LedgerAccount payable = findOrCreateAccount(organizationId, LedgerAccountCode.MERCHANT_PAYABLE);

        LedgerTransaction transaction = new LedgerTransaction(organizationId, paymentId, refundId, LedgerTransactionType.REFUND);
        transactionRepository.save(transaction);

        // Reverse of postCapture, per ADR-0008: Dr Merchant Payable / Cr
        // Provider Settlement Receivable.
        entryRepository.save(new LedgerEntry(transaction, payable, LedgerEntryType.DEBIT, amount, currency));
        entryRepository.save(new LedgerEntry(transaction, receivable, LedgerEntryType.CREDIT, amount, currency));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntrySummary> listEntries(
            UUID organizationId, UUID paymentId, Instant createdAfter, Instant createdBefore, Integer limit) {
        int effectiveLimit = Math.min(limit != null ? limit : DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        return entryRepository.search(organizationId, paymentId, createdAfter, createdBefore, effectiveLimit)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private LedgerAccount findOrCreateAccount(UUID organizationId, LedgerAccountCode code) {
        return accountRepository.findByOrganizationIdAndCode(organizationId, code.dbCode())
                .orElseGet(() -> createOrFetchExisting(organizationId, code));
    }

    private LedgerAccount createOrFetchExisting(UUID organizationId, LedgerAccountCode code) {
        try {
            return accountWriter.create(organizationId, code);
        } catch (DataIntegrityViolationException lostRace) {
            return accountRepository.findByOrganizationIdAndCode(organizationId, code.dbCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Ledger account " + code.dbCode() + " disappeared after a unique constraint conflict"));
        }
    }

    private LedgerEntrySummary toSummary(LedgerEntry entry) {
        return new LedgerEntrySummary(
                entry.getId(),
                entry.getLedgerTransaction().getId(),
                entry.getLedgerTransaction().getPaymentId(),
                entry.getLedgerAccount().getCode(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getCreatedAt());
    }
}
