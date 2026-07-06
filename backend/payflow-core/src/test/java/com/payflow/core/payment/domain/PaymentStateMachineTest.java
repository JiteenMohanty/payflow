package com.payflow.core.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    @Test
    void allowsTheHappyPath() {
        assertThatCode(() -> {
            PaymentStateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.AUTHORIZED);
            PaymentStateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED);
            PaymentStateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.REFUNDED);
        }).doesNotThrowAnyException();
    }

    @Test
    void allowsCreatedToFail() {
        assertThatCode(() -> PaymentStateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.FAILED))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAuthorizedToExpireOrCancel() {
        assertThatCode(() -> PaymentStateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.EXPIRED))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentStateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CANCELED))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsPartialThenFullRefund() {
        assertThatCode(() -> {
            PaymentStateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED);
            PaymentStateMachine.validateTransition(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.PARTIALLY_REFUNDED);
            PaymentStateMachine.validateTransition(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsSkippingAuthorization() {
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.CAPTURED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void rejectsDoubleCapture() {
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.CAPTURED, PaymentStatus.CAPTURED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void rejectsTransitionsOutOfTerminalStates() {
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.FAILED, PaymentStatus.AUTHORIZED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void rejectsReAuthorizingAnAlreadyAuthorizedPayment() {
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(PaymentStatus.AUTHORIZED, PaymentStatus.AUTHORIZED))
                .isInstanceOf(IllegalPaymentTransitionException.class);
    }
}
