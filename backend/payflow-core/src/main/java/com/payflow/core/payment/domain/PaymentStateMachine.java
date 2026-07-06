package com.payflow.core.payment.domain;

import java.util.Map;
import java.util.Set;

/**
 * Pure validator for the payment lifecycle - no Spring, no persistence, so it
 * is directly unit-testable. The happy path is exactly
 * CREATED -&gt; AUTHORIZED -&gt; CAPTURED -&gt; REFUNDED; the other edges exist for
 * failure/expiry/partial-refund handling. See EDD section 8.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED),
            PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.EXPIRED, PaymentStatus.CANCELED),
            PaymentStatus.CAPTURED, Set.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
            PaymentStatus.PARTIALLY_REFUNDED, Set.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
    );

    private PaymentStateMachine() {
    }

    public static void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalPaymentTransitionException(from, to);
        }
    }
}
