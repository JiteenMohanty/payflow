package com.payflow.core.refund.application;

import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.ledger.application.LedgerService;
import com.payflow.core.payment.application.PaymentRefundContext;
import com.payflow.core.payment.application.PaymentRefundSupport;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRefundResult;
import com.payflow.core.provider.ProviderRefundStatus;
import com.payflow.core.provider.ProviderRegistry;
import com.payflow.core.refund.domain.Refund;
import com.payflow.core.refund.persistence.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentRefundSupport paymentRefundSupport;
    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private ProviderClient providerClient;
    @Mock
    private LedgerService ledgerService;

    private RefundService refundService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refundService = new RefundService(refundRepository, paymentRefundSupport, providerRegistry, ledgerService);
    }

    @Test
    void fullRefundUsesTheRemainingBalanceWhenNoAmountIsRequested() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("0.00"));
        stubSuccessfulProviderRefund();

        RefundSummary summary = refundService.createRefund(organizationId, paymentId, null, "requested_by_customer");

        assertThat(summary.amount()).isEqualByComparingTo("100.00");
        verify(paymentRefundSupport).applyRefund(organizationId, paymentId, new BigDecimal("100.00"));
        verify(ledgerService).postRefund(any(), any(), any(), org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")), any());
    }

    @Test
    void partialRefundUsesTheRequestedAmount() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("0.00"));
        stubSuccessfulProviderRefund();

        RefundSummary summary = refundService.createRefund(organizationId, paymentId, new BigDecimal("40.00"), null);

        assertThat(summary.amount()).isEqualByComparingTo("40.00");
        verify(paymentRefundSupport).applyRefund(organizationId, paymentId, new BigDecimal("40.00"));
    }

    @Test
    void secondPartialRefundIsBoundedByWhatIsStillLeftAfterTheFirst() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("60.00"));
        stubSuccessfulProviderRefund();

        RefundSummary summary = refundService.createRefund(organizationId, paymentId, null, null);

        assertThat(summary.amount()).isEqualByComparingTo("40.00");
    }

    @Test
    void rejectsARefundAmountExceedingTheRemainingRefundableBalance() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("60.00"));

        assertThatThrownBy(() -> refundService.createRefund(organizationId, paymentId, new BigDecimal("50.00"), null))
                .isInstanceOf(DomainValidationException.class);

        verify(providerRegistry, never()).resolve(any());
    }

    @Test
    void rejectsAZeroOrNegativeRefundAmount() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("0.00"));

        assertThatThrownBy(() -> refundService.createRefund(organizationId, paymentId, new BigDecimal("0.00"), null))
                .isInstanceOf(DomainValidationException.class);

        verify(providerRegistry, never()).resolve(any());
    }

    @Test
    void nothingIsPersistedWhenThePaymentIsNotInARefundableState() {
        when(paymentRefundSupport.loadForRefund(organizationId, paymentId))
                .thenThrow(new DomainValidationException("Payment is not in a refundable state: AUTHORIZED"));

        assertThatThrownBy(() -> refundService.createRefund(organizationId, paymentId, null, null))
                .isInstanceOf(DomainValidationException.class);

        verify(refundRepository, never()).save(any());
    }

    @Test
    void nothingIsPersistedWhenTheProviderReportsFailure() {
        stubRefundableContext(new BigDecimal("100.00"), new BigDecimal("0.00"));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.refund(any())).thenReturn(new ProviderRefundResult(ProviderRefundStatus.FAILED, "network_error"));

        assertThatThrownBy(() -> refundService.createRefund(organizationId, paymentId, null, null))
                .isInstanceOf(DomainValidationException.class);

        verify(refundRepository, never()).save(any());
        verify(paymentRefundSupport, never()).applyRefund(any(), any(), any());
        verify(ledgerService, never()).postRefund(any(), any(), any(), any(), any());
    }

    private void stubRefundableContext(BigDecimal capturedAmount, BigDecimal refundedAmount) {
        when(paymentRefundSupport.loadForRefund(organizationId, paymentId)).thenReturn(new PaymentRefundContext(
                paymentId, capturedAmount, refundedAmount, "USD", "mock_ch_existing", ProviderCode.MOCK));
    }

    private void stubSuccessfulProviderRefund() {
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.refund(any())).thenReturn(new ProviderRefundResult(ProviderRefundStatus.REFUNDED, null));
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund refund = inv.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
            return refund;
        });
    }
}
