package com.payflow.core.payment.scheduled;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.payment.application.PaymentReconciliationSupport;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
import com.payflow.core.provider.ProviderChargeStatus;
import com.payflow.core.provider.ProviderChargeStatusResult;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationSweeperTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentReconciliationSupport paymentReconciliationSupport;
    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private ProviderClient providerClient;

    private ReconciliationSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new ReconciliationSweeper(paymentRepository, paymentReconciliationSupport, providerRegistry,
                new ReconciliationSweepProperties(900_000, 15));
    }

    @Test
    void reconcilesAPaymentTheProviderReportsAsCaptured() {
        Payment payment = stalePayment();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(payment));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.checkStatus(payment.getProviderReference()))
                .thenReturn(new ProviderChargeStatusResult(ProviderChargeStatus.CAPTURED, new BigDecimal("50.00"), "USD"));

        sweeper.sweep();

        verify(paymentReconciliationSupport).reconcileCaptureConfirmation(
                ProviderCode.MOCK, payment.getProviderReference(), new BigDecimal("50.00"), "USD");
    }

    @Test
    void leavesAPaymentTheProviderStillReportsAsAuthorizedAlone() {
        Payment payment = stalePayment();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(payment));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.checkStatus(payment.getProviderReference()))
                .thenReturn(new ProviderChargeStatusResult(ProviderChargeStatus.AUTHORIZED, null, null));

        sweeper.sweep();

        verify(paymentReconciliationSupport, never()).reconcileCaptureConfirmation(any(), any(), any(), any());
    }

    @Test
    void doesNotActOnANotFoundResult() {
        Payment payment = stalePayment();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(payment));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.checkStatus(payment.getProviderReference()))
                .thenReturn(new ProviderChargeStatusResult(ProviderChargeStatus.NOT_FOUND, null, null));

        sweeper.sweep();

        verify(paymentReconciliationSupport, never()).reconcileCaptureConfirmation(any(), any(), any(), any());
    }

    @Test
    void oneProviderCommunicationFailureDoesNotStopTheRestOfTheSweep() {
        Payment failing = stalePayment();
        Payment ok = stalePayment();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(failing, ok));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.checkStatus(failing.getProviderReference())).thenThrow(new RuntimeException("provider down"));
        when(providerClient.checkStatus(ok.getProviderReference()))
                .thenReturn(new ProviderChargeStatusResult(ProviderChargeStatus.CAPTURED, new BigDecimal("10.00"), "USD"));

        sweeper.sweep();

        verify(paymentReconciliationSupport).reconcileCaptureConfirmation(
                ProviderCode.MOCK, ok.getProviderReference(), new BigDecimal("10.00"), "USD");
    }

    private Payment stalePayment() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "USD", null, null);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.markAuthorized(UUID.randomUUID(), ProviderCode.MOCK, "mock_ch_" + UUID.randomUUID());
        return payment;
    }
}
