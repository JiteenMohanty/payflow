package com.payflow.core.payment.domain;

import com.payflow.core.common.exception.PayFlowException;

public class IllegalPaymentTransitionException extends PayFlowException {

    public IllegalPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("illegal_transition", "Cannot transition payment from " + from + " to " + to);
    }
}
