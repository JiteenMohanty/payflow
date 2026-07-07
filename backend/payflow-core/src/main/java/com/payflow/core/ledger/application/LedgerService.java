package com.payflow.core.ledger.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumed by PaymentService.capture() within the same @Transactional
 * method as the payment's own state change - see ADR-0008. Only capture
 * posting exists for M4; postRefund() is additive when M5 needs it.
 */
public interface LedgerService {

    void postCapture(UUID organizationId, UUID paymentId, BigDecimal amount, String currency);
}
