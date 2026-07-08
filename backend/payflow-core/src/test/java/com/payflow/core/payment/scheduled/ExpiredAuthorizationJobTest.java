package com.payflow.core.payment.scheduled;

import com.payflow.core.payment.application.PaymentService;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredAuthorizationJobTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;

    private ExpiredAuthorizationJob job;

    @BeforeEach
    void setUp() {
        job = new ExpiredAuthorizationJob(paymentRepository, paymentService, new ExpiredAuthorizationProperties(3_600_000, 168));
    }

    @Test
    void expiresEveryStalePaymentReturnedByTheQuery() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(stalePayment(firstId), stalePayment(secondId)));

        job.sweep();

        verify(paymentService).expireAuthorization(firstId);
        verify(paymentService).expireAuthorization(secondId);
    }

    @Test
    void oneFailingPaymentDoesNotStopTheRestOfTheSweep() {
        UUID failingId = UUID.randomUUID();
        UUID okId = UUID.randomUUID();
        when(paymentRepository.findByStatusAndAuthorizedAtBefore(eq(PaymentStatus.AUTHORIZED), any()))
                .thenReturn(List.of(stalePayment(failingId), stalePayment(okId)));
        doThrow(new RuntimeException("db hiccup")).when(paymentService).expireAuthorization(failingId);

        job.sweep();

        verify(paymentService).expireAuthorization(okId);
    }

    private Payment stalePayment(UUID id) {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "USD", null, null);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
