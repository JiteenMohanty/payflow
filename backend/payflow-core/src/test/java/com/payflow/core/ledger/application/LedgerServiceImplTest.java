package com.payflow.core.ledger.application;

import com.payflow.core.ledger.domain.LedgerAccount;
import com.payflow.core.ledger.domain.LedgerAccountCode;
import com.payflow.core.ledger.domain.LedgerEntry;
import com.payflow.core.ledger.domain.LedgerEntryType;
import com.payflow.core.ledger.persistence.LedgerAccountRepository;
import com.payflow.core.ledger.persistence.LedgerEntryRepository;
import com.payflow.core.ledger.persistence.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private LedgerAccountRepository accountRepository;
    @Mock
    private LedgerAccountWriter accountWriter;
    @Mock
    private LedgerTransactionRepository transactionRepository;
    @Mock
    private LedgerEntryRepository entryRepository;

    private LedgerServiceImpl ledgerService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceImpl(accountRepository, accountWriter, transactionRepository, entryRepository);
    }

    @Test
    void postCapturePostsABalancedDebitAndCredit() {
        when(accountRepository.findByOrganizationIdAndCode(eq(organizationId), any()))
                .thenReturn(Optional.empty());
        when(accountWriter.create(eq(organizationId), any(LedgerAccountCode.class)))
                .thenAnswer(inv -> account(organizationId, inv.getArgument(1)));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var transaction = inv.getArgument(0, com.payflow.core.ledger.domain.LedgerTransaction.class);
            ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
            return transaction;
        });
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(entryRepository.save(entryCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.postCapture(organizationId, paymentId, new BigDecimal("50.00"), "USD");

        var entries = entryCaptor.getAllValues();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(entries.get(0).getLedgerAccount().getCode()).isEqualTo(LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE.dbCode());
        assertThat(entries.get(1).getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(entries.get(1).getLedgerAccount().getCode()).isEqualTo(LedgerAccountCode.MERCHANT_PAYABLE.dbCode());
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("50.00");
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void reusesExistingAccountsInsteadOfCreatingDuplicates() {
        LedgerAccount receivable = account(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE);
        LedgerAccount payable = account(organizationId, LedgerAccountCode.MERCHANT_PAYABLE);
        when(accountRepository.findByOrganizationIdAndCode(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE.dbCode()))
                .thenReturn(Optional.of(receivable));
        when(accountRepository.findByOrganizationIdAndCode(organizationId, LedgerAccountCode.MERCHANT_PAYABLE.dbCode()))
                .thenReturn(Optional.of(payable));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var transaction = inv.getArgument(0, com.payflow.core.ledger.domain.LedgerTransaction.class);
            ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
            return transaction;
        });
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.postCapture(organizationId, paymentId, new BigDecimal("15.00"), "USD");

        verify(accountWriter, never()).create(any(), any());
    }

    @Test
    void lostAccountCreationRaceFallsBackToTheWinningRow() {
        LedgerAccount winningAccount = account(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE);
        when(accountRepository.findByOrganizationIdAndCode(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE.dbCode()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winningAccount));
        when(accountRepository.findByOrganizationIdAndCode(organizationId, LedgerAccountCode.MERCHANT_PAYABLE.dbCode()))
                .thenReturn(Optional.of(account(organizationId, LedgerAccountCode.MERCHANT_PAYABLE)));
        when(accountWriter.create(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var transaction = inv.getArgument(0, com.payflow.core.ledger.domain.LedgerTransaction.class);
            ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
            return transaction;
        });
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(entryRepository.save(entryCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.postCapture(organizationId, paymentId, new BigDecimal("25.00"), "USD");

        assertThat(entryCaptor.getAllValues().get(0).getLedgerAccount()).isSameAs(winningAccount);
        verify(accountRepository, times(2))
                .findByOrganizationIdAndCode(organizationId, LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE.dbCode());
    }

    @Test
    void postRefundPostsTheReverseOfACapture() {
        UUID refundId = UUID.randomUUID();
        when(accountRepository.findByOrganizationIdAndCode(eq(organizationId), any()))
                .thenReturn(Optional.empty());
        when(accountWriter.create(eq(organizationId), any(LedgerAccountCode.class)))
                .thenAnswer(inv -> account(organizationId, inv.getArgument(1)));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            var transaction = inv.getArgument(0, com.payflow.core.ledger.domain.LedgerTransaction.class);
            ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
            return transaction;
        });
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(entryRepository.save(entryCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.postRefund(organizationId, paymentId, refundId, new BigDecimal("30.00"), "USD");

        var entries = entryCaptor.getAllValues();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(entries.get(0).getLedgerAccount().getCode()).isEqualTo(LedgerAccountCode.MERCHANT_PAYABLE.dbCode());
        assertThat(entries.get(1).getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(entries.get(1).getLedgerAccount().getCode()).isEqualTo(LedgerAccountCode.PROVIDER_SETTLEMENT_RECEIVABLE.dbCode());
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("30.00");
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("30.00");
    }

    private LedgerAccount account(UUID organizationId, LedgerAccountCode code) {
        LedgerAccount account = new LedgerAccount(organizationId, code);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
