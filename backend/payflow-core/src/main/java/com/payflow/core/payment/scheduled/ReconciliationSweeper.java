package com.payflow.core.payment.scheduled;

import com.payflow.core.payment.application.PaymentReconciliationSupport;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
import com.payflow.core.provider.ProviderChargeStatus;
import com.payflow.core.provider.ProviderChargeStatusResult;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The backstop ADR-0011 requires: "cross-checks payments stuck in a
 * non-terminal state past an expected window against the provider
 * directly." Reuses PaymentReconciliationSupport.reconcileCaptureConfirmation
 * - the same path the inbound webhook (M7) uses - so a payment recovered
 * here posts an identical ledger entry and outbox event, per ADR-0011's own
 * "must both funnel through the same state transition and ledger-posting
 * logic." Runs on a much shorter window than ExpiredAuthorizationJob: this
 * is specifically about catching a lost webhook quickly, not giving up on
 * an authorization.
 */
@Component
@RequiredArgsConstructor
public class ReconciliationSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationSweeper.class);

    private final PaymentRepository paymentRepository;
    private final PaymentReconciliationSupport paymentReconciliationSupport;
    private final ProviderRegistry providerRegistry;
    private final ReconciliationSweepProperties properties;

    @Scheduled(fixedDelayString = "${payflow.reconciliation.sweep-interval-ms:900000}")
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.staleWindowMinutes()));
        List<Payment> stuck = paymentRepository.findByStatusAndAuthorizedAtBefore(PaymentStatus.AUTHORIZED, cutoff);
        for (Payment payment : stuck) {
            reconcileOne(payment);
        }
    }

    private void reconcileOne(Payment payment) {
        try {
            ProviderClient client = providerRegistry.resolve(payment.getProviderCode());
            ProviderChargeStatusResult result = client.checkStatus(payment.getProviderReference());

            if (result.status() == ProviderChargeStatus.CAPTURED) {
                paymentReconciliationSupport.reconcileCaptureConfirmation(
                        payment.getProviderCode(), payment.getProviderReference(), result.amount(), result.currency());
            }
            // AUTHORIZED: genuinely still authorized at the provider too -
            // nothing to reconcile; ExpiredAuthorizationJob is what
            // eventually gives up on it if it stays stuck.
            // NOT_FOUND: the provider has no record of this charge - a
            // transient provider-side issue shouldn't corrupt local state,
            // so this is logged, not acted on.
            if (result.status() == ProviderChargeStatus.NOT_FOUND) {
                log.warn("Reconciliation sweep: provider has no record of charge {} for payment {}",
                        payment.getProviderReference(), payment.getId());
            }
        } catch (Exception e) {
            log.warn("Reconciliation sweep failed to reach provider for payment {}", payment.getId(), e);
        }
    }
}
