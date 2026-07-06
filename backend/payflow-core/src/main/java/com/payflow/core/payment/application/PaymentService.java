package com.payflow.core.payment.application;

import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.merchant.application.MerchantLookupService;
import com.payflow.core.merchant.application.MerchantSummary;
import com.payflow.core.merchant.application.ProviderAccountResolver;
import com.payflow.core.merchant.application.ProviderAccountSummary;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentActor;
import com.payflow.core.payment.domain.PaymentStateMachine;
import com.payflow.core.payment.domain.PaymentStateTransition;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
import com.payflow.core.payment.persistence.PaymentStateTransitionRepository;
import com.payflow.core.provider.ProviderAuthorizationRequest;
import com.payflow.core.provider.ProviderAuthorizationResult;
import com.payflow.core.provider.ProviderCaptureRequest;
import com.payflow.core.provider.ProviderCaptureResult;
import com.payflow.core.provider.ProviderCaptureStatus;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final MerchantLookupService merchantLookupService;
    private final ProviderAccountResolver providerAccountResolver;
    private final ProviderRegistry providerRegistry;

    @Transactional
    public PaymentSummary createPayment(
            UUID organizationId, UUID merchantId, BigDecimal amount, String currency,
            String description, Map<String, Object> metadata) {
        requireMerchantInOrganization(organizationId, merchantId);

        Payment payment = new Payment(organizationId, merchantId, amount, currency, description, metadata);
        paymentRepository.save(payment);
        recordTransition(payment, null, PaymentStatus.CREATED, PaymentActor.API, null);

        return toSummary(payment);
    }

    @Transactional
    public PaymentSummary authorize(UUID organizationId, UUID paymentId, UUID providerAccountIdOverride) {
        Payment payment = loadOwnedPayment(organizationId, paymentId);
        // Validate before calling the provider - not after - so a payment that
        // isn't CREATED can never trigger a second provider-side authorization.
        PaymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.AUTHORIZED);

        ProviderAccountSummary providerAccount = providerAccountIdOverride != null
                ? providerAccountResolver.resolveById(payment.getMerchantId(), providerAccountIdOverride)
                : providerAccountResolver.resolveDefault(payment.getMerchantId());

        ProviderClient client = providerRegistry.resolve(providerAccount.providerCode());
        ProviderAuthorizationResult result = client.authorize(new ProviderAuthorizationRequest(
                payment.getAmount(), payment.getCurrency(), payment.getId().toString()));

        PaymentStatus from = payment.getStatus();
        switch (result.status()) {
            case AUTHORIZED -> {
                payment.markAuthorized(providerAccount.id(), providerAccount.providerCode(), result.providerReference());
                recordTransition(payment, from, PaymentStatus.AUTHORIZED, PaymentActor.API, null);
            }
            case DECLINED -> {
                payment.markAuthorizationFailed();
                recordTransition(payment, from, PaymentStatus.FAILED, PaymentActor.API, result.failureReason());
            }
        }

        return toSummary(payment);
    }

    @Transactional
    public PaymentSummary capture(UUID organizationId, UUID paymentId, BigDecimal requestedAmount) {
        Payment payment = loadOwnedPayment(organizationId, paymentId);
        // Same reasoning as authorize(): validate before calling the provider,
        // not after, so an illegal capture attempt never reaches it at all.
        PaymentStateMachine.validateTransition(payment.getStatus(), PaymentStatus.CAPTURED);

        BigDecimal captureAmount = requestedAmount != null ? requestedAmount : payment.remainingCapturableAmount();
        if (captureAmount.signum() <= 0) {
            throw new DomainValidationException("Capture amount must be greater than zero");
        }
        if (captureAmount.compareTo(payment.remainingCapturableAmount()) > 0) {
            throw new DomainValidationException("Capture amount exceeds the remaining capturable amount");
        }

        ProviderClient client = providerRegistry.resolve(payment.getProviderCode());
        ProviderCaptureResult result = client.capture(
                new ProviderCaptureRequest(payment.getProviderReference(), captureAmount, payment.getCurrency()));

        if (result.status() == ProviderCaptureStatus.FAILED) {
            throw new DomainValidationException("Capture failed: " + result.failureReason());
        }

        PaymentStatus from = payment.getStatus();
        payment.markCaptured(captureAmount);
        recordTransition(payment, from, PaymentStatus.CAPTURED, PaymentActor.API, null);

        return toSummary(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDetail getById(UUID organizationId, UUID paymentId) {
        Payment payment = loadOwnedPayment(organizationId, paymentId);
        List<PaymentTransitionSummary> transitions = transitionRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId)
                .stream()
                .map(this::toTransitionSummary)
                .toList();
        return new PaymentDetail(toSummary(payment), transitions);
    }

    @Transactional(readOnly = true)
    public List<PaymentSummary> list(UUID organizationId, UUID merchantId, PaymentStatus status, Integer limit) {
        int effectiveLimit = Math.min(limit != null ? limit : DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        return paymentRepository.search(organizationId, merchantId, status, PageRequest.of(0, effectiveLimit)).stream()
                .map(this::toSummary)
                .toList();
    }

    private void requireMerchantInOrganization(UUID organizationId, UUID merchantId) {
        MerchantSummary merchant = merchantLookupService.getById(merchantId);
        if (!merchant.organizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Merchant not found: " + merchantId);
        }
    }

    private Payment loadOwnedPayment(UUID organizationId, UUID paymentId) {
        return paymentRepository.findByIdAndOrganizationId(paymentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private void recordTransition(Payment payment, PaymentStatus from, PaymentStatus to, PaymentActor actor, String reason) {
        transitionRepository.save(new PaymentStateTransition(payment, from, to, actor, reason));
    }

    private PaymentSummary toSummary(Payment payment) {
        return new PaymentSummary(
                payment.getId(),
                payment.getMerchantId(),
                payment.getProviderReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getCapturedAmount(),
                payment.getRefundedAmount(),
                payment.getCreatedAt(),
                payment.getAuthorizedAt(),
                payment.getCapturedAt());
    }

    private PaymentTransitionSummary toTransitionSummary(PaymentStateTransition transition) {
        return new PaymentTransitionSummary(
                transition.getFromStatus(), transition.getToStatus(), transition.getActor(),
                transition.getReason(), transition.getCreatedAt());
    }
}
