package com.payflow.core;

import com.payflow.core.ledger.application.LedgerEntrySummary;
import com.payflow.core.ledger.application.LedgerQueryService;
import com.payflow.core.ledger.application.LedgerService;
import com.payflow.core.ledger.domain.LedgerAccount;
import com.payflow.core.ledger.domain.LedgerEntry;
import com.payflow.core.ledger.domain.LedgerEntryType;
import com.payflow.core.ledger.domain.LedgerTransaction;
import com.payflow.core.ledger.domain.LedgerTransactionType;
import com.payflow.core.ledger.persistence.LedgerAccountRepository;
import com.payflow.core.ledger.persistence.LedgerEntryRepository;
import com.payflow.core.ledger.persistence.LedgerTransactionRepository;
import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the ledger module directly (bypassing HTTP for the parts that
 * need the Mock Provider running live - see the M2/M4 manual verification
 * notes in CHANGELOG.md) plus the real database triggers from V5__ledger.sql
 * that a Mockito-mocked repository can never actually prove.
 */
class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private LedgerQueryService ledgerQueryService;
    @Autowired
    private LedgerAccountRepository accountRepository;
    @Autowired
    private LedgerTransactionRepository transactionRepository;
    @Autowired
    private LedgerEntryRepository entryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postCaptureCreatesBalancedEntriesAndReusesAccountsAcrossCaptures() {
        Tenant tenant = provisionTenant();
        UUID paymentIdA = createPayment(tenant);
        UUID paymentIdB = createPayment(tenant);

        ledgerService.postCapture(tenant.organizationId(), paymentIdA, new BigDecimal("30.00"), "USD");
        ledgerService.postCapture(tenant.organizationId(), paymentIdB, new BigDecimal("40.00"), "USD");

        List<LedgerEntrySummary> entries = ledgerQueryService.listEntries(tenant.organizationId(), null, null, null, null);
        assertThat(entries).hasSize(4);

        long accountsForOrg = accountRepository.findAll().stream()
                .filter(account -> account.getOrganizationId().equals(tenant.organizationId()))
                .count();
        assertThat(accountsForOrg).isEqualTo(2);

        List<LedgerEntrySummary> entriesForPaymentA =
                ledgerQueryService.listEntries(tenant.organizationId(), paymentIdA, null, null, null);
        assertThat(entriesForPaymentA).hasSize(2);
        assertThat(entriesForPaymentA).extracting(LedgerEntrySummary::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("30.00"));
    }

    @Test
    void unbalancedLedgerEntryIsRejectedByTheDatabaseAtCommitTime() {
        Tenant tenant = provisionTenant();
        UUID paymentId = createPayment(tenant);

        LedgerAccount account = accountRepository.save(
                new LedgerAccount(tenant.organizationId(), com.payflow.core.ledger.domain.LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE));
        LedgerTransaction transaction = transactionRepository.save(
                new LedgerTransaction(tenant.organizationId(), paymentId, LedgerTransactionType.ADJUSTMENT));

        assertThatThrownBy(() -> entryRepository.saveAndFlush(
                new LedgerEntry(transaction, account, LedgerEntryType.DEBIT, new BigDecimal("10.00"), "USD")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ledgerEntriesCannotBeUpdatedOrDeletedAtTheDatabaseLevel() {
        Tenant tenant = provisionTenant();
        UUID paymentId = createPayment(tenant);
        ledgerService.postCapture(tenant.organizationId(), paymentId, new BigDecimal("20.00"), "USD");

        UUID entryId = jdbcTemplate.queryForObject(
                "SELECT le.id FROM ledger_entries le "
                        + "JOIN ledger_transactions lt ON lt.id = le.ledger_transaction_id "
                        + "WHERE lt.payment_id = ? LIMIT 1",
                UUID.class, paymentId);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_entries SET amount = 999.00 WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_entries WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID createPayment(Tenant tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId(), new BigDecimal("1.00"), "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        return response.getBody().id();
    }
}
