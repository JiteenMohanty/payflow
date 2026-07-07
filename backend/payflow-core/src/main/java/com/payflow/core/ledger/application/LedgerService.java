package com.payflow.core.ledger.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumed by PaymentService.capture() and RefundService.createRefund()
 * within the same @Transactional method as the payment/refund's own state
 * change - see ADR-0008.
 */
public interface LedgerService {

    void postCapture(UUID organizationId, UUID paymentId, BigDecimal amount, String currency);

    void postRefund(UUID organizationId, UUID paymentId, UUID refundId, BigDecimal amount, String currency);
}
