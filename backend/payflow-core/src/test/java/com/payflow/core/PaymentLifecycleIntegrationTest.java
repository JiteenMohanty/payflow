package com.payflow.core;

import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentDetailResponse;
import com.payflow.core.payment.api.PaymentResponse;
import com.payflow.core.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the create/get/list/validation surface of the payment API over real
 * HTTP and real Postgres. Authorize/capture need the separate Mock Provider
 * service actually running, so those are exercised manually - see the M2
 * verification notes in CHANGELOG.md.
 */
class PaymentLifecycleIntegrationTest extends AbstractIntegrationTest {

    private UUID organizationId;
    private UUID merchantId;
    private String apiKey;
    private String dashboardAccessToken;

    @BeforeEach
    void setUpOrganizationMerchantAndApiKey() {
        Tenant tenant = provisionTenant();
        organizationId = tenant.organizationId();
        merchantId = tenant.merchantId();
        apiKey = tenant.apiKey();
        dashboardAccessToken = tenant.accessToken();
    }

    @Test
    void createPaymentSucceedsWithValidMerchant() {
        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST,
                withApiKey(new CreatePaymentRequest(merchantId, new BigDecimal("100.00"), "USD", "Order #1", null)),
                PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.getBody().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void createPaymentRejectsMerchantFromAnotherOrganization() {
        Tenant otherTenant = provisionTenant();

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST,
                withApiKey(new CreatePaymentRequest(otherTenant.merchantId(), new BigDecimal("50.00"), "USD", null, null)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPaymentRejectsNonPositiveAmount() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST,
                withApiKey(new CreatePaymentRequest(merchantId, new BigDecimal("0.00"), "USD", null, null)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getPaymentReturnsDetailWithInitialTransition() {
        PaymentResponse created = createPayment(new BigDecimal("75.00"));

        ResponseEntity<PaymentDetailResponse> response = restTemplate.exchange(
                "/v1/payments/" + created.id(), HttpMethod.GET, withApiKey(), PaymentDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().transitions()).hasSize(1);
        assertThat(response.getBody().transitions().get(0).fromStatus()).isNull();
        assertThat(response.getBody().transitions().get(0).toStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void listPaymentsFiltersByMerchant() {
        createPayment(new BigDecimal("10.00"));
        createPayment(new BigDecimal("20.00"));
        Tenant otherTenant = provisionTenant();
        createPaymentFor(otherTenant, new BigDecimal("30.00"));

        ResponseEntity<PaymentResponse[]> response = restTemplate.exchange(
                "/v1/payments?merchantId=" + merchantId, HttpMethod.GET, withApiKey(), PaymentResponse[].class);

        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void dashboardJwtCannotCallMerchantFacingPaymentEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(dashboardAccessToken);
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(merchantId, new BigDecimal("10.00"), "USD", null, null), headers);

        ResponseEntity<String> response = restTemplate.exchange("/v1/payments", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void apiKeyFromAnotherOrganizationCannotSeeThisPayment() {
        PaymentResponse created = createPayment(new BigDecimal("40.00"));
        Tenant otherTenant = provisionTenant();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(otherTenant.apiKey());
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/payments/" + created.id(), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PaymentResponse createPayment(BigDecimal amount) {
        return createPaymentFor(new Tenant(organizationId, merchantId, apiKey, dashboardAccessToken), amount);
    }

    private PaymentResponse createPaymentFor(Tenant tenant, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId(), amount, "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpEntity<Void> withApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> withApiKey(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return new HttpEntity<>(body, headers);
    }
}
