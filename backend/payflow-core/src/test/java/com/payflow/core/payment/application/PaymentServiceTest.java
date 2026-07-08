package com.payflow.core.payment.application;

import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.ledger.application.LedgerService;
import com.payflow.core.merchant.application.MerchantLookupService;
import com.payflow.core.merchant.application.MerchantSummary;
import com.payflow.core.merchant.application.ProviderAccountResolver;
import com.payflow.core.merchant.application.ProviderAccountSummary;
import com.payflow.core.merchant.domain.MerchantStatus;
import com.payflow.core.merchant.domain.ProviderAccountStatus;
import com.payflow.core.outbox.application.OutboxWriter;
import com.payflow.core.payment.domain.IllegalPaymentTransitionException;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
import com.payflow.core.payment.persistence.PaymentStateTransitionRepository;
import com.payflow.core.provider.ProviderAuthorizationResult;
import com.payflow.core.provider.ProviderAuthorizationStatus;
import com.payflow.core.provider.ProviderCaptureResult;
import com.payflow.core.provider.ProviderCaptureStatus;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentStateTransitionRepository transitionRepository;
    @Mock
    private MerchantLookupService merchantLookupService;
    @Mock
    private ProviderAccountResolver providerAccountResolver;
    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private ProviderClient providerClient;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private OutboxWriter outboxWriter;

    private PaymentService paymentService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, transitionRepository, merchantLookupService,
                providerAccountResolver, providerRegistry, ledgerService, outboxWriter);
    }

    @Test
    void createPaymentSucceedsWhenMerchantBelongsToOrganization() {
        when(merchantLookupService.getById(merchantId))
                .thenReturn(new MerchantSummary(merchantId, organizationId, "Acme", "USD", MerchantStatus.ACTIVE));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentSummary summary = paymentService.createPayment(
                organizationId, merchantId, new BigDecimal("100.00"), "USD", "Order #1", null);

        assertThat(summary.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(summary.amount()).isEqualByComparingTo("100.00");
        verify(outboxWriter).write(eq("PAYMENT"), any(), eq("payment.created"), eq(OutboxTopics.PAYMENTS), any());
    }

    @Test
    void createPaymentRejectsMerchantFromAnotherOrganization() {
        UUID otherOrgId = UUID.randomUUID();
        when(merchantLookupService.getById(merchantId))
                .thenReturn(new MerchantSummary(merchantId, otherOrgId, "Acme", "USD", MerchantStatus.ACTIVE));

        assertThatThrownBy(() -> paymentService.createPayment(
                organizationId, merchantId, new BigDecimal("100.00"), "USD", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorizeTransitionsToAuthorizedWhenProviderAuthorizes() {
        Payment payment = newPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));
        when(providerAccountResolver.resolveDefault(merchantId)).thenReturn(providerAccount());
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.authorize(any())).thenReturn(
                new ProviderAuthorizationResult(ProviderAuthorizationStatus.AUTHORIZED, "mock_ch_123", null));

        PaymentSummary summary = paymentService.authorize(organizationId, payment.getId(), null);

        assertThat(summary.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(summary.providerReference()).isEqualTo("mock_ch_123");
    }

    @Test
    void authorizeTransitionsToFailedWhenProviderDeclines() {
        Payment payment = newPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));
        when(providerAccountResolver.resolveDefault(merchantId)).thenReturn(providerAccount());
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.authorize(any())).thenReturn(
                new ProviderAuthorizationResult(ProviderAuthorizationStatus.DECLINED, null, "insufficient_funds"));

        PaymentSummary summary = paymentService.authorize(organizationId, payment.getId(), null);

        assertThat(summary.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void authorizeRejectsAnAlreadyAuthorizedPaymentWithoutCallingTheProviderAgain() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.authorize(organizationId, payment.getId(), null))
                .isInstanceOf(IllegalPaymentTransitionException.class);

        verify(providerRegistry, never()).resolve(any());
    }

    @Test
    void captureTransitionsToCapturedAndPostsTheLedgerEntryInTheSameCall() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.capture(any())).thenReturn(new ProviderCaptureResult(ProviderCaptureStatus.CAPTURED, null));

        PaymentSummary summary = paymentService.capture(organizationId, payment.getId(), null);

        assertThat(summary.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(summary.capturedAmount()).isEqualByComparingTo("100.00");
        verify(ledgerService).postCapture(organizationId, payment.getId(), new BigDecimal("100.00"), "USD");
        verify(outboxWriter).write(eq("PAYMENT"), any(), eq("payment.captured"), eq(OutboxTopics.PAYMENTS), any());
        verify(outboxWriter).write(eq("LEDGER_TRANSACTION"), any(), eq("ledger.transaction_recorded"), eq(OutboxTopics.LEDGER), any());
    }

    @Test
    void captureRejectsAmountExceedingRemaining() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.capture(organizationId, payment.getId(), new BigDecimal("150.00")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void captureRejectsWhenProviderReportsFailure() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));
        when(providerRegistry.resolve(ProviderCode.MOCK)).thenReturn(providerClient);
        when(providerClient.capture(any())).thenReturn(new ProviderCaptureResult(ProviderCaptureStatus.FAILED, "network_error"));

        assertThatThrownBy(() -> paymentService.capture(organizationId, payment.getId(), null))
                .isInstanceOf(DomainValidationException.class);

        verify(ledgerService, never()).postCapture(any(), any(), any(), any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void captureRejectsPaymentThatWasNeverAuthorizedWithoutCallingTheProvider() {
        Payment payment = newPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.capture(organizationId, payment.getId(), null))
                .isInstanceOf(IllegalPaymentTransitionException.class);

        verify(providerRegistry, never()).resolve(any());
    }

    @Test
    void loadForRefundReturnsContextWhenPaymentIsCaptured() {
        Payment payment = capturedPayment(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        PaymentRefundContext context = paymentService.loadForRefund(organizationId, payment.getId());

        assertThat(context.capturedAmount()).isEqualByComparingTo("100.00");
        assertThat(context.refundedAmount()).isEqualByComparingTo("0.00");
        assertThat(context.providerReference()).isEqualTo("mock_ch_existing");
        assertThat(context.providerCode()).isEqualTo(ProviderCode.MOCK);
    }

    @Test
    void loadForRefundRejectsPaymentThatWasNeverCaptured() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.loadForRefund(organizationId, payment.getId()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void applyRefundTransitionsToPartiallyRefundedWhenLessThanTheCapturedAmount() {
        Payment payment = capturedPayment(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        paymentService.applyRefund(organizationId, payment.getId(), new BigDecimal("30.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void applyRefundTransitionsToRefundedWhenTheFullCapturedAmountHasBeenRefunded() {
        Payment payment = capturedPayment(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(paymentRepository.lockByIdAndOrganizationId(payment.getId(), organizationId)).thenReturn(Optional.of(payment));

        paymentService.applyRefund(organizationId, payment.getId(), new BigDecimal("60.00"));
        paymentService.applyRefund(organizationId, payment.getId(), new BigDecimal("40.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void reconcileCaptureConfirmationAppliesCaptureWhenPaymentIsAuthorized() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByProviderCodeAndProviderReference(ProviderCode.MOCK, "mock_ch_existing"))
                .thenReturn(Optional.of(payment));

        paymentService.reconcileCaptureConfirmation(ProviderCode.MOCK, "mock_ch_existing", new BigDecimal("100.00"), "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("100.00");
        verify(ledgerService).postCapture(organizationId, payment.getId(), new BigDecimal("100.00"), "USD");
        verify(outboxWriter).write(eq("PAYMENT"), any(), eq("payment.captured"), eq(OutboxTopics.PAYMENTS), any());
    }

    @Test
    void reconcileCaptureConfirmationIsANoOpWhenPaymentIsAlreadyCaptured() {
        Payment payment = capturedPayment(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(paymentRepository.lockByProviderCodeAndProviderReference(ProviderCode.MOCK, "mock_ch_existing"))
                .thenReturn(Optional.of(payment));

        paymentService.reconcileCaptureConfirmation(ProviderCode.MOCK, "mock_ch_existing", new BigDecimal("100.00"), "USD");

        verify(ledgerService, never()).postCapture(any(), any(), any(), any());
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void reconcileCaptureConfirmationIsANoOpWhenNoMatchingPaymentExists() {
        when(paymentRepository.lockByProviderCodeAndProviderReference(ProviderCode.MOCK, "mock_ch_unknown"))
                .thenReturn(Optional.empty());

        paymentService.reconcileCaptureConfirmation(ProviderCode.MOCK, "mock_ch_unknown", new BigDecimal("100.00"), "USD");

        verify(ledgerService, never()).postCapture(any(), any(), any(), any());
    }

    @Test
    void reconcileCaptureConfirmationIsANoOpWhenTheWebhookAmountExceedsWhatCanStillBeCaptured() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockByProviderCodeAndProviderReference(ProviderCode.MOCK, "mock_ch_existing"))
                .thenReturn(Optional.of(payment));

        paymentService.reconcileCaptureConfirmation(ProviderCode.MOCK, "mock_ch_existing", new BigDecimal("999.00"), "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        verify(ledgerService, never()).postCapture(any(), any(), any(), any());
    }

    @Test
    void expireAuthorizationTransitionsAuthorizedPaymentToExpired() {
        Payment payment = authorizedPayment(new BigDecimal("100.00"));
        when(paymentRepository.lockById(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.expireAuthorization(payment.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(outboxWriter).write(eq("PAYMENT"), any(), eq("payment.expired"), eq(OutboxTopics.PAYMENTS), any());
    }

    @Test
    void expireAuthorizationIsANoOpWhenThePaymentIsNoLongerAuthorized() {
        Payment payment = capturedPayment(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(paymentRepository.lockById(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.expireAuthorization(payment.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(outboxWriter, never()).write(any(), any(), eq("payment.expired"), any(), any());
    }

    @Test
    void expireAuthorizationIsANoOpWhenNoMatchingPaymentExists() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.lockById(paymentId)).thenReturn(Optional.empty());

        paymentService.expireAuthorization(paymentId);

        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    private Payment newPayment(BigDecimal amount) {
        Payment payment = new Payment(organizationId, merchantId, amount, "USD", null, null);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    private Payment authorizedPayment(BigDecimal amount) {
        Payment payment = newPayment(amount);
        payment.markAuthorized(UUID.randomUUID(), ProviderCode.MOCK, "mock_ch_existing");
        return payment;
    }

    private Payment capturedPayment(BigDecimal amount, BigDecimal capturedAmount) {
        Payment payment = authorizedPayment(amount);
        payment.markCaptured(capturedAmount);
        return payment;
    }

    private ProviderAccountSummary providerAccount() {
        return new ProviderAccountSummary(UUID.randomUUID(), merchantId, ProviderCode.MOCK, true, ProviderAccountStatus.ACTIVE, null);
    }
}
