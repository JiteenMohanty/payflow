package com.payflow.core.payment.application;

import com.payflow.core.payment.domain.PaymentStatus;

import java.util.List;
import java.util.UUID;

/**
 * The read-only surface the dashboard module (M12) depends on, kept
 * separate from PaymentService's own command-oriented public methods -
 * named in the EDD's module table since Phase 0 but never actually
 * extracted until now, since nothing needed it as its own seam before the
 * dashboard's cross-module read access made that seam necessary. Same
 * multi-interface pattern PaymentService already uses for
 * PaymentRefundSupport/PaymentReconciliationSupport.
 */
public interface PaymentQueryService {

    PaymentDetail getById(UUID organizationId, UUID paymentId);

    List<PaymentSummary> list(UUID organizationId, UUID merchantId, PaymentStatus status, Integer limit);

    DashboardSummary getSummary(UUID organizationId);
}
