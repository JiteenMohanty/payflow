package com.payflow.core.payment.scheduled;

import com.payflow.core.payment.application.PaymentService;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.payment.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * "AUTHORIZED -&gt; EXPIRED: auth window elapsed" from the payment state
 * diagram (EDD section 8). Purely time-based - unlike ReconciliationSweeper,
 * this never calls the provider; a real auth hold expiring is provider-side
 * behavior PayFlow mirrors locally, not something to cross-check. Runs on a
 * much longer window than ReconciliationSweeper by design: a merchant
 * legitimately not capturing immediately (ship-then-capture flows) is
 * normal on the scale of hours, but not indefinitely.
 */
@Component
@RequiredArgsConstructor
public class ExpiredAuthorizationJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthorizationJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ExpiredAuthorizationProperties properties;

    @Scheduled(fixedDelayString = "${payflow.expired-authorization.sweep-interval-ms:3600000}")
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.authWindowHours()));
        List<Payment> stale = paymentRepository.findByStatusAndAuthorizedAtBefore(PaymentStatus.AUTHORIZED, cutoff);
        for (Payment payment : stale) {
            try {
                paymentService.expireAuthorization(payment.getId());
            } catch (Exception e) {
                log.warn("Failed to expire authorization for payment {}", payment.getId(), e);
            }
        }
    }
}
