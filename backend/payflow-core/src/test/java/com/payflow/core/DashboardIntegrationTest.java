package com.payflow.core;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.dashboard.api.DashboardLedgerEntryResponse;
import com.payflow.core.dashboard.api.DashboardPaymentDetailResponse;
import com.payflow.core.dashboard.api.DashboardPaymentResponse;
import com.payflow.core.dashboard.api.DashboardRefundResponse;
import com.payflow.core.dashboard.api.DashboardSummaryResponse;
import com.payflow.core.dashboard.api.DashboardWebhookDeliveryResponse;
import com.payflow.core.dashboard.api.DashboardWebhookEndpointResponse;
import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentResponse;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the dashboard module (M12, EDD section 5.2) end to end over real
 * HTTP: JWT dashboard-session RBAC gating, plus DashboardController's
 * delegation into each owning module's existing query/command surface.
 * Authorize/capture and a genuinely successful refund need the separate
 * Mock Provider service actually running (same limitation as
 * PaymentLifecycleIntegrationTest), so those paths are exercised manually -
 * see the M12 verification notes in CHANGELOG.md. The webhook endpoint here
 * is inserted directly via the repository rather than through the
 * registration API, same reasoning as WebhookDeliveryIntegrationTest:
 * WebhookUrlValidator does real DNS resolution, which a sandboxed test run
 * cannot depend on.
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookEndpointRepository webhookEndpointRepository;
    @Autowired
    private SymmetricEncryptor encryptor;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Tenant tenant;

    @BeforeEach
    void setUpTenant() {
        tenant = provisionTenant();
    }

    @Test
    void summaryAggregatesCreatedPaymentsAcrossAllWindows() {
        createPayment(new BigDecimal("10.00"));
        createPayment(new BigDecimal("20.00"));

        ResponseEntity<DashboardSummaryResponse> response = restTemplate.exchange(
                dashboardPath("/summary"), HttpMethod.GET, authed(), DashboardSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().windows()).hasSize(3);
        response.getBody().windows().forEach(window -> {
            assertThat(window.paymentCount()).isEqualTo(2);
            assertThat(window.totalVolume()).isEqualByComparingTo("30.00");
            assertThat(window.byStatus()).hasSize(1);
            assertThat(window.byStatus().get(0).status()).isEqualTo(PaymentStatus.CREATED);
            assertThat(window.byStatus().get(0).count()).isEqualTo(2);
        });
    }

    @Test
    void paymentsListFiltersByMerchantSameAsMerchantFacingApi() {
        createPayment(new BigDecimal("10.00"));
        createPayment(new BigDecimal("20.00"));

        ResponseEntity<DashboardPaymentResponse[]> response = restTemplate.exchange(
                dashboardPath("/payments?merchantId=" + tenant.merchantId()), HttpMethod.GET, authed(),
                DashboardPaymentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void paymentDetailIncludesInitialTransition() {
        PaymentResponse created = createPayment(new BigDecimal("75.00"));

        ResponseEntity<DashboardPaymentDetailResponse> response = restTemplate.exchange(
                dashboardPath("/payments/" + created.id()), HttpMethod.GET, authed(), DashboardPaymentDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().payment().id()).isEqualTo(created.id());
        assertThat(response.getBody().transitions()).hasSize(1);
        assertThat(response.getBody().transitions().get(0).toStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void ledgerEndpointReturnsEmptyForAnUncapturedPayment() {
        PaymentResponse created = createPayment(new BigDecimal("15.00"));

        ResponseEntity<DashboardLedgerEntryResponse[]> response = restTemplate.exchange(
                dashboardPath("/payments/" + created.id() + "/ledger"), HttpMethod.GET, authed(),
                DashboardLedgerEntryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void refundRejectsAnUncapturedPaymentWithADomainValidationError() {
        PaymentResponse created = createPayment(new BigDecimal("15.00"));

        ResponseEntity<String> response = restTemplate.exchange(
                dashboardPath("/payments/" + created.id() + "/refunds"), HttpMethod.POST, authed(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("not in a refundable state");
    }

    @Test
    void refundIsForbiddenForAnAnalystRole() {
        PaymentResponse created = createPayment(new BigDecimal("15.00"));
        downgradeSoleMemberToAnalyst();

        ResponseEntity<DashboardRefundResponse> response = restTemplate.exchange(
                dashboardPath("/payments/" + created.id() + "/refunds"), HttpMethod.POST, authed(), DashboardRefundResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void webhookEndpointsAndDeliveriesAreVisibleFromTheDashboard() {
        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenant.organizationId(), "https://example.com/hooks",
                encryptor.encrypt("whsec_dashboard_test".getBytes(StandardCharsets.UTF_8)), List.of("payment.created"));
        webhookEndpointRepository.save(endpoint);

        ResponseEntity<DashboardWebhookEndpointResponse[]> endpoints = restTemplate.exchange(
                dashboardPath("/webhook-endpoints"), HttpMethod.GET, authed(), DashboardWebhookEndpointResponse[].class);
        assertThat(endpoints.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endpoints.getBody()).hasSize(1);
        assertThat(endpoints.getBody()[0].id()).isEqualTo(endpoint.getId());

        ResponseEntity<DashboardWebhookDeliveryResponse[]> deliveries = restTemplate.exchange(
                dashboardPath("/webhook-endpoints/" + endpoint.getId() + "/deliveries"), HttpMethod.GET, authed(),
                DashboardWebhookDeliveryResponse[].class);
        assertThat(deliveries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deliveries.getBody()).isEmpty();
    }

    @Test
    void apiKeyCannotCallDashboardEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                dashboardPath("/summary"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userNotAMemberOfTheOrganizationCannotAccessItsDashboard() {
        Tenant otherTenant = provisionTenant();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(otherTenant.accessToken());
        ResponseEntity<String> response = restTemplate.exchange(
                dashboardPath("/summary"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * There is no HTTP-level way to provision a non-OWNER member yet (no
     * invite endpoint exists), and OrganizationMember has no role setter -
     * raw SQL against the sole membership row provisionTenant() created is
     * the only way to exercise the ADMIN/OWNER-only refund rule, same
     * "direct persistence access for what HTTP can't reach" precedent as
     * LedgerIntegrationTest's JdbcTemplate usage.
     */
    private void downgradeSoleMemberToAnalyst() {
        jdbcTemplate.update(
                "UPDATE organization_members SET role = 'ANALYST' WHERE organization_id = ?",
                tenant.organizationId());
    }

    private PaymentResponse createPayment(BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId(), amount, "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String dashboardPath(String suffix) {
        return "/v1/organizations/" + tenant.organizationId() + "/dashboard" + suffix;
    }

    private HttpEntity<Void> authed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.accessToken());
        return new HttpEntity<>(headers);
    }
}
