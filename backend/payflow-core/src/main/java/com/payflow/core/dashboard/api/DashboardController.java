package com.payflow.core.dashboard.api;

import com.payflow.core.ledger.application.LedgerEntrySummary;
import com.payflow.core.ledger.application.LedgerQueryService;
import com.payflow.core.organization.application.OrganizationAccessGuard;
import com.payflow.core.organization.domain.OrganizationRole;
import com.payflow.core.payment.application.DashboardStatusCount;
import com.payflow.core.payment.application.DashboardSummary;
import com.payflow.core.payment.application.DashboardWindowSummary;
import com.payflow.core.payment.application.PaymentDetail;
import com.payflow.core.payment.application.PaymentQueryService;
import com.payflow.core.payment.application.PaymentSummary;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.refund.application.RefundService;
import com.payflow.core.refund.application.RefundSummary;
import com.payflow.core.webhook.outbound.application.WebhookDeliveryQueryService;
import com.payflow.core.webhook.outbound.application.WebhookDeliverySummary;
import com.payflow.core.webhook.outbound.application.WebhookEndpointSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard-facing read/action surface for the Admin Dashboard frontend
 * (M12, EDD section 5.2) - JWT dashboard-session authenticated only, RBAC
 * gated per-endpoint via OrganizationAccessGuard.requireDashboardMembership.
 * No domain or persistence of its own (EDD section 3's dashboard module
 * row): every method here is pure delegation to the owning module's
 * existing query/command interface, converted to this module's own
 * response DTOs so that the dashboard's wire format can evolve
 * independently of each owning module's own merchant-facing API shape.
 */
@RestController
@RequestMapping("/v1/organizations/{organizationId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final PaymentQueryService paymentQueryService;
    private final LedgerQueryService ledgerQueryService;
    private final WebhookDeliveryQueryService webhookDeliveryQueryService;
    private final RefundService refundService;
    private final OrganizationAccessGuard accessGuard;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@PathVariable UUID organizationId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return toSummaryResponse(paymentQueryService.getSummary(organizationId));
    }

    @GetMapping("/payments")
    public List<DashboardPaymentResponse> payments(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Integer limit) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return paymentQueryService.list(organizationId, merchantId, status, limit).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @GetMapping("/payments/{paymentId}")
    public DashboardPaymentDetailResponse payment(@PathVariable UUID organizationId, @PathVariable UUID paymentId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return toPaymentDetailResponse(paymentQueryService.getById(organizationId, paymentId));
    }

    @GetMapping("/payments/{paymentId}/ledger")
    public List<DashboardLedgerEntryResponse> ledger(@PathVariable UUID organizationId, @PathVariable UUID paymentId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return ledgerQueryService.listEntries(organizationId, paymentId, null, null, null).stream()
                .map(this::toLedgerEntryResponse)
                .toList();
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<DashboardRefundResponse> refund(
            @PathVariable UUID organizationId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody(required = false) CreateDashboardRefundRequest request) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        var amount = request != null ? request.amount() : null;
        var reason = request != null ? request.reason() : null;
        RefundSummary summary = refundService.createRefund(organizationId, paymentId, amount, reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRefundResponse(summary));
    }

    @GetMapping("/webhook-endpoints")
    public List<DashboardWebhookEndpointResponse> webhookEndpoints(@PathVariable UUID organizationId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return webhookDeliveryQueryService.listEndpoints(organizationId).stream()
                .map(this::toWebhookEndpointResponse)
                .toList();
    }

    @GetMapping("/webhook-endpoints/{endpointId}/deliveries")
    public List<DashboardWebhookDeliveryResponse> webhookDeliveries(
            @PathVariable UUID organizationId, @PathVariable UUID endpointId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return webhookDeliveryQueryService.listDeliveries(organizationId, endpointId).stream()
                .map(this::toWebhookDeliveryResponse)
                .toList();
    }

    private DashboardSummaryResponse toSummaryResponse(DashboardSummary summary) {
        List<DashboardWindowSummaryResponse> windows = summary.windows().stream()
                .map(this::toWindowSummaryResponse)
                .toList();
        return new DashboardSummaryResponse(windows);
    }

    private DashboardWindowSummaryResponse toWindowSummaryResponse(DashboardWindowSummary window) {
        List<DashboardStatusCountResponse> byStatus = window.byStatus().stream()
                .map(this::toStatusCountResponse)
                .toList();
        return new DashboardWindowSummaryResponse(window.window(), window.paymentCount(), window.totalVolume(), byStatus);
    }

    private DashboardStatusCountResponse toStatusCountResponse(DashboardStatusCount count) {
        return new DashboardStatusCountResponse(count.status(), count.count(), count.totalAmount());
    }

    private DashboardPaymentResponse toPaymentResponse(PaymentSummary summary) {
        return new DashboardPaymentResponse(
                summary.id(), summary.merchantId(), summary.providerReference(), summary.amount(), summary.currency(),
                summary.description(), summary.status(), summary.capturedAmount(), summary.refundedAmount(),
                summary.createdAt(), summary.authorizedAt(), summary.capturedAt());
    }

    private DashboardPaymentDetailResponse toPaymentDetailResponse(PaymentDetail detail) {
        List<DashboardPaymentTransitionResponse> transitions = detail.transitions().stream()
                .map(t -> new DashboardPaymentTransitionResponse(t.fromStatus(), t.toStatus(), t.actor(), t.reason(), t.createdAt()))
                .toList();
        return new DashboardPaymentDetailResponse(toPaymentResponse(detail.payment()), transitions);
    }

    private DashboardLedgerEntryResponse toLedgerEntryResponse(LedgerEntrySummary summary) {
        return new DashboardLedgerEntryResponse(
                summary.id(), summary.ledgerTransactionId(), summary.paymentId(), summary.accountCode(),
                summary.entryType(), summary.amount(), summary.currency(), summary.createdAt());
    }

    private DashboardRefundResponse toRefundResponse(RefundSummary summary) {
        return new DashboardRefundResponse(
                summary.id(), summary.paymentId(), summary.amount(), summary.currency(),
                summary.status(), summary.reason(), summary.providerReference(), summary.createdAt());
    }

    private DashboardWebhookEndpointResponse toWebhookEndpointResponse(WebhookEndpointSummary summary) {
        return new DashboardWebhookEndpointResponse(
                summary.id(), summary.url(), summary.subscribedEvents(), summary.status(), summary.createdAt());
    }

    private DashboardWebhookDeliveryResponse toWebhookDeliveryResponse(WebhookDeliverySummary summary) {
        return new DashboardWebhookDeliveryResponse(
                summary.id(), summary.eventType(), summary.attemptNumber(), summary.status(),
                summary.responseCode(), summary.nextRetryAt(), summary.createdAt());
    }
}
