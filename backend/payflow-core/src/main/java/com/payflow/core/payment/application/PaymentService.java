package com.payflow.core.payment.application;

import com.payflow.core.common.event.LedgerEventPayload;
import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.common.event.PaymentEventPayload;
import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.ledger.application.LedgerService;
import com.payflow.core.merchant.application.MerchantLookupService;
import com.payflow.core.merchant.application.MerchantSummary;
import com.payflow.core.merchant.application.ProviderAccountResolver;
import com.payflow.core.merchant.application.ProviderAccountSummary;
import com.payflow.core.outbox.application.OutboxWriter;
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
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Only capture confirmation is reconciled from an inbound provider webhook
 * (see reconcileCaptureConfirmation) - authorize and refund reconciliation
 * are deliberately out of scope for M7. Both share a gap capture doesn't
 * have: authorize() and RefundService.createRefund() roll back their entire
 * transaction on a provider communication failure (see both methods),
 * persisting nothing - so a lost sync response for either leaves no
 * committed row for a webhook to "confirm." Reconciling them would mean
 * creating state from the webhook alone, a materially different operation
 * this milestone's worked example (EDD section 7.2) doesn't cover.
 */
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentRefundSupport, PaymentReconciliationSupport {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    private static final String TRANSITIONS_METRIC = "payflow.payments.transitions";

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final MerchantLookupService merchantLookupService;
    private final ProviderAccountResolver providerAccountResolver;
    private final ProviderRegistry providerRegistry;
    private final LedgerService ledgerService;
    private final OutboxWriter outboxWriter;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PaymentSummary createPayment(
            UUID organizationId, UUID merchantId, BigDecimal amount, String currency,
            String description, Map<String, Object> metadata) {
        requireMerchantInOrganization(organizationId, merchantId);

        Payment payment = new Payment(organizationId, merchantId, amount, currency, description, metadata);
        paymentRepository.save(payment);
        recordTransition(payment, null, PaymentStatus.CREATED, PaymentActor.API, null);
        emitPaymentEvent(payment, "payment.created");

        return toSummary(payment);
    }

    @Transactional
    public PaymentSummary authorize(UUID organizationId, UUID paymentId, UUID providerAccountIdOverride) {
        Payment payment = loadOwnedPaymentForUpdate(organizationId, paymentId);
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
                emitPaymentEvent(payment, "payment.authorized");
            }
            case DECLINED -> {
                payment.markAuthorizationFailed();
                recordTransition(payment, from, PaymentStatus.FAILED, PaymentActor.API, result.failureReason());
                emitPaymentEvent(payment, "payment.authorization_failed");
            }
        }

        return toSummary(payment);
    }

    @Transactional
    public PaymentSummary capture(UUID organizationId, UUID paymentId, BigDecimal requestedAmount) {
        Payment payment = loadOwnedPaymentForUpdate(organizationId, paymentId);
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

        applyCapture(payment, captureAmount, PaymentActor.API, null);

        return toSummary(payment);
    }

    /**
     * Reconciliation path for a lost/timed-out synchronous capture response
     * (ADR-0011, EDD section 7.2) - the provider has already confirmed the
     * capture via webhook, so unlike capture() above, this never calls the
     * provider again. Idempotent: a webhook retried after the payment is
     * already CAPTURED (or beyond) is a safe no-op, matching "confirm, don't
     * blindly overwrite" from ADR-0011. amount is trusted from the webhook
     * payload (the reconciliation source of truth) but still bounded by
     * remainingCapturableAmount as a defensive check against a stale or
     * malformed event - a real signature only proves the event's origin,
     * not that its content still makes sense against current state.
     */
    @Override
    @Transactional
    public void reconcileCaptureConfirmation(ProviderCode providerCode, String providerReference, BigDecimal amount, String currency) {
        paymentRepository.lockByProviderCodeAndProviderReference(providerCode, providerReference)
                .ifPresent(payment -> {
                    if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
                        // Already CAPTURED (or beyond): idempotent no-op.
                        // Any other state is unexpected for a capture
                        // confirmation - ignored rather than blindly applied.
                        return;
                    }
                    if (amount.signum() <= 0 || amount.compareTo(payment.remainingCapturableAmount()) > 0) {
                        return;
                    }
                    applyCapture(payment, amount, PaymentActor.PROVIDER_WEBHOOK, "reconciled from provider webhook");
                });
    }

    private void applyCapture(Payment payment, BigDecimal captureAmount, PaymentActor actor, String reason) {
        PaymentStatus from = payment.getStatus();
        payment.markCaptured(captureAmount);
        recordTransition(payment, from, PaymentStatus.CAPTURED, actor, reason);
        // Same transaction as the state change above - see ADR-0008. A
        // captured payment with no ledger posting must never be a reachable
        // state, not just an unlikely one.
        ledgerService.postCapture(payment.getOrganizationId(), payment.getId(), captureAmount, payment.getCurrency());
        emitPaymentEvent(payment, "payment.captured");
        emitLedgerEvent(payment.getOrganizationId(), payment.getId(), "CAPTURE", captureAmount, payment.getCurrency());
    }

    /**
     * Not readOnly, despite never mutating anything itself: it locks the row
     * (see loadOwnedPaymentForUpdate) so that lock is still held when
     * RefundService.createRefund() later calls applyRefund() within the
     * same transaction - Postgres rejects SELECT ... FOR UPDATE inside a
     * connection actually marked read-only.
     */
    @Override
    @Transactional
    public PaymentRefundContext loadForRefund(UUID organizationId, UUID paymentId) {
        Payment payment = loadOwnedPaymentForUpdate(organizationId, paymentId);
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new DomainValidationException("Payment is not in a refundable state: " + payment.getStatus());
        }
        return new PaymentRefundContext(
                payment.getId(), payment.getCapturedAmount(), payment.getRefundedAmount(),
                payment.getCurrency(), payment.getProviderReference(), payment.getProviderCode());
    }

    @Override
    @Transactional
    public void applyRefund(UUID organizationId, UUID paymentId, BigDecimal refundAmount) {
        Payment payment = loadOwnedPaymentForUpdate(organizationId, paymentId);
        BigDecimal newRefundedAmount = payment.getRefundedAmount().add(refundAmount);
        PaymentStatus targetStatus = newRefundedAmount.compareTo(payment.getCapturedAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;

        PaymentStatus from = payment.getStatus();
        payment.applyRefund(refundAmount, targetStatus);
        recordTransition(payment, from, targetStatus, PaymentActor.API, null);
    }

    /**
     * ExpiredAuthorizationJob's per-payment mutation (M9, "auth window
     * elapsed" edge in EDD section 8's state diagram). No organizationId -
     * the job sweeps system-wide, not on behalf of one tenant's request -
     * so it locks by id alone (PaymentRepository.lockById). Idempotent: a
     * payment that moved on (captured, canceled) before the job reached it
     * is a safe no-op, same "confirm current state, don't blindly act"
     * discipline as reconcileCaptureConfirmation.
     */
    @Transactional
    public void expireAuthorization(UUID paymentId) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
                return;
            }
            PaymentStatus from = payment.getStatus();
            payment.markExpired();
            recordTransition(payment, from, PaymentStatus.EXPIRED, PaymentActor.SCHEDULED_JOB, "authorization window elapsed");
            emitPaymentEvent(payment, "payment.expired");
        });
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

    /**
     * For any caller that loads a payment in order to mutate it - see
     * PaymentRepository.lockByIdAndOrganizationId for why this is load-
     * bearing, not defensive.
     */
    private Payment loadOwnedPaymentForUpdate(UUID organizationId, UUID paymentId) {
        return paymentRepository.lockByIdAndOrganizationId(paymentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * The one choke point every payment status change already passes
     * through, so it doubles as the business-metric emission point for
     * "payments by status" (ADR-0012) without a separate counter call at
     * every one of the eight call sites above.
     */
    private void recordTransition(Payment payment, PaymentStatus from, PaymentStatus to, PaymentActor actor, String reason) {
        transitionRepository.save(new PaymentStateTransition(payment, from, to, actor, reason));
        meterRegistry.counter(TRANSITIONS_METRIC, "to_status", to.name()).increment();
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

    /**
     * payment.capture_failed and payment.canceled (both listed in EDD
     * section 6) are still deliberately not emitted: a failed capture
     * throws and rolls back the whole transaction before anything commits
     * (see capture() above), so there is no committed row to attach an
     * event to, and nothing cancels a payment yet. payment.expired is now
     * wired - see expireAuthorization().
     */
    private void emitPaymentEvent(Payment payment, String eventType) {
        PaymentEventPayload payload = new PaymentEventPayload(
                eventType, payment.getId(), payment.getOrganizationId(), payment.getMerchantId(), payment.getStatus().name(),
                payment.getAmount(), payment.getCurrency(), Instant.now());
        outboxWriter.write("PAYMENT", payment.getId(), eventType, OutboxTopics.PAYMENTS, payload);
    }

    /**
     * ledger.transaction_recorded is emitted here, not by the ledger module
     * itself - ledger depends only on common (EDD section 3) and must not
     * depend on outbox.
     */
    private void emitLedgerEvent(UUID organizationId, UUID paymentId, String transactionType, BigDecimal amount, String currency) {
        LedgerEventPayload payload = new LedgerEventPayload(organizationId, paymentId, transactionType, amount, currency, Instant.now());
        outboxWriter.write("LEDGER_TRANSACTION", paymentId, "ledger.transaction_recorded", OutboxTopics.LEDGER, payload);
    }
}
