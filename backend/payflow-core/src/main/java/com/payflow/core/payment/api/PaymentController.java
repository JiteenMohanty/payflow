package com.payflow.core.payment.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.payment.application.PaymentDetail;
import com.payflow.core.payment.application.PaymentService;
import com.payflow.core.payment.application.PaymentSummary;
import com.payflow.core.payment.domain.PaymentStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Merchant-facing lifecycle API - API-key authenticated only. The
 * organization is resolved from the API key's TenantContext, never from a
 * path variable, since an API key is already scoped to exactly one org.
 */
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        UUID organizationId = requireApiKeyOrganization();
        PaymentSummary summary = paymentService.createPayment(
                organizationId, request.merchantId(), request.amount(), request.currency().toUpperCase(),
                request.description(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    @PostMapping("/{paymentId}/authorize")
    public PaymentResponse authorize(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) AuthorizePaymentRequest request) {
        UUID organizationId = requireApiKeyOrganization();
        UUID providerAccountOverride = request != null ? request.providerAccountId() : null;
        return toResponse(paymentService.authorize(organizationId, paymentId, providerAccountOverride));
    }

    @PostMapping("/{paymentId}/capture")
    public PaymentResponse capture(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) CapturePaymentRequest request) {
        UUID organizationId = requireApiKeyOrganization();
        var amount = request != null ? request.amount() : null;
        return toResponse(paymentService.capture(organizationId, paymentId, amount));
    }

    @GetMapping("/{paymentId}")
    public PaymentDetailResponse get(@PathVariable UUID paymentId) {
        UUID organizationId = requireApiKeyOrganization();
        return toDetailResponse(paymentService.getById(organizationId, paymentId));
    }

    @GetMapping
    public List<PaymentResponse> list(
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Integer limit) {
        UUID organizationId = requireApiKeyOrganization();
        return paymentService.list(organizationId, merchantId, status, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private UUID requireApiKeyOrganization() {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.API_KEY) {
            throw new AccessDeniedException("This endpoint requires an API key");
        }
        return context.organizationId();
    }

    private PaymentResponse toResponse(PaymentSummary summary) {
        return new PaymentResponse(
                summary.id(), summary.merchantId(), summary.providerReference(), summary.amount(), summary.currency(),
                summary.description(), summary.status(), summary.capturedAmount(), summary.refundedAmount(),
                summary.createdAt(), summary.authorizedAt(), summary.capturedAt());
    }

    private PaymentDetailResponse toDetailResponse(PaymentDetail detail) {
        List<PaymentTransitionResponse> transitions = detail.transitions().stream()
                .map(t -> new PaymentTransitionResponse(t.fromStatus(), t.toStatus(), t.actor(), t.reason(), t.createdAt()))
                .toList();
        return new PaymentDetailResponse(toResponse(detail.payment()), transitions);
    }
}
