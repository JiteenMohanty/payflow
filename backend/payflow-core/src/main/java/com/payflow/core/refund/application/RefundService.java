package com.payflow.core.refund.application;

import com.payflow.core.common.event.LedgerEventPayload;
import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.common.event.RefundEventPayload;
import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.ledger.application.LedgerService;
import com.payflow.core.outbox.application.OutboxWriter;
import com.payflow.core.payment.application.PaymentRefundContext;
import com.payflow.core.payment.application.PaymentRefundSupport;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRefundRequest;
import com.payflow.core.provider.ProviderRefundResult;
import com.payflow.core.provider.ProviderRefundStatus;
import com.payflow.core.provider.ProviderRegistry;
import com.payflow.core.refund.domain.Refund;
import com.payflow.core.refund.domain.RefundStatus;
import com.payflow.core.refund.persistence.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRefundSupport paymentRefundSupport;
    private final ProviderRegistry providerRegistry;
    private final LedgerService ledgerService;
    private final OutboxWriter outboxWriter;

    @Transactional
    public RefundSummary createRefund(UUID organizationId, UUID paymentId, BigDecimal requestedAmount, String reason) {
        PaymentRefundContext payment = paymentRefundSupport.loadForRefund(organizationId, paymentId);

        BigDecimal remaining = payment.capturedAmount().subtract(payment.refundedAmount());
        BigDecimal refundAmount = requestedAmount != null ? requestedAmount : remaining;
        if (refundAmount.signum() <= 0) {
            throw new DomainValidationException("Refund amount must be greater than zero");
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new DomainValidationException("Refund amount exceeds the remaining refundable amount");
        }

        ProviderClient client = providerRegistry.resolve(payment.providerCode());
        ProviderRefundResult result = client.refund(
                new ProviderRefundRequest(payment.providerReference(), refundAmount, payment.currency()));

        if (result.status() == ProviderRefundStatus.FAILED) {
            // Nothing persisted, nothing changed - same as a failed capture,
            // this is safe to retry with the same or a corrected amount.
            throw new DomainValidationException("Refund failed: " + result.failureReason());
        }

        Refund refund = new Refund(organizationId, paymentId, refundAmount, payment.currency(),
                RefundStatus.SUCCEEDED, reason, payment.providerReference());
        refundRepository.save(refund);

        // Same transaction as the refund row and the payment's state change -
        // see ADR-0008. A refund with no ledger posting must never be a
        // reachable state.
        paymentRefundSupport.applyRefund(organizationId, paymentId, refundAmount);
        ledgerService.postRefund(organizationId, paymentId, refund.getId(), refundAmount, payment.currency());
        emitRefundEvent(refund);
        emitLedgerEvent(organizationId, paymentId, refundAmount, payment.currency());

        return toSummary(refund);
    }

    @Transactional(readOnly = true)
    public RefundSummary getById(UUID organizationId, UUID refundId) {
        Refund refund = refundRepository.findByIdAndOrganizationId(refundId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        return toSummary(refund);
    }

    /**
     * refund.created and refund.failed (both listed in EDD section 6) are
     * deliberately not emitted: a refund resolves synchronously in one call
     * (see Refund's own class-level note), so there is no distinct "created"
     * moment before "succeeded", and a failed provider refund persists
     * nothing at all, so there is no committed row to attach refund.failed
     * to - same reasoning as PaymentService skipping capture_failed.
     */
    private void emitRefundEvent(Refund refund) {
        RefundEventPayload payload = new RefundEventPayload(
                refund.getId(), refund.getPaymentId(), refund.getOrganizationId(), refund.getAmount(),
                refund.getCurrency(), Instant.now());
        outboxWriter.write("REFUND", refund.getId(), "refund.succeeded", OutboxTopics.REFUNDS, payload);
    }

    private void emitLedgerEvent(UUID organizationId, UUID paymentId, BigDecimal amount, String currency) {
        LedgerEventPayload payload = new LedgerEventPayload(organizationId, paymentId, "REFUND", amount, currency, Instant.now());
        outboxWriter.write("LEDGER_TRANSACTION", paymentId, "ledger.transaction_recorded", OutboxTopics.LEDGER, payload);
    }

    private RefundSummary toSummary(Refund refund) {
        return new RefundSummary(
                refund.getId(), refund.getPaymentId(), refund.getAmount(), refund.getCurrency(),
                refund.getStatus(), refund.getReason(), refund.getProviderReference(), refund.getCreatedAt());
    }
}
