package com.payflow.core.refund.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.refund.application.RefundService;
import com.payflow.core.refund.application.RefundSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Merchant-facing, API-key authenticated only - same convention as
 * PaymentController. Create is nested under /v1/payments per EDD section
 * 5.1; fetch is flat under /v1/refunds since a refund id is already globally
 * unique within the organization.
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/v1/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> create(
            @PathVariable UUID paymentId,
            @Valid @RequestBody(required = false) CreateRefundRequest request) {
        UUID organizationId = requireApiKeyOrganization();
        var amount = request != null ? request.amount() : null;
        var reason = request != null ? request.reason() : null;
        RefundSummary summary = refundService.createRefund(organizationId, paymentId, amount, reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    @GetMapping("/v1/refunds/{refundId}")
    public RefundResponse get(@PathVariable UUID refundId) {
        UUID organizationId = requireApiKeyOrganization();
        return toResponse(refundService.getById(organizationId, refundId));
    }

    private UUID requireApiKeyOrganization() {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.API_KEY) {
            throw new AccessDeniedException("This endpoint requires an API key");
        }
        return context.organizationId();
    }

    private RefundResponse toResponse(RefundSummary summary) {
        return new RefundResponse(
                summary.id(), summary.paymentId(), summary.amount(), summary.currency(),
                summary.status(), summary.reason(), summary.providerReference(), summary.createdAt());
    }
}
