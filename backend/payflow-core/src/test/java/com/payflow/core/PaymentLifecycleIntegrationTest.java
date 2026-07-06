package com.payflow.core;

import com.payflow.core.merchant.api.CreateMerchantRequest;
import com.payflow.core.merchant.api.CreateProviderAccountRequest;
import com.payflow.core.merchant.api.MerchantResponse;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.organization.api.CreateApiKeyRequest;
import com.payflow.core.organization.api.CreateApiKeyResponse;
import com.payflow.core.organization.api.CreateOrganizationRequest;
import com.payflow.core.organization.api.CreateOrganizationResponse;
import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentDetailResponse;
import com.payflow.core.payment.api.PaymentResponse;
import com.payflow.core.payment.domain.PaymentStatus;
import com.payflow.core.security.api.LoginRequest;
import com.payflow.core.security.api.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the create/get/list/validation surface of the payment API over real
 * HTTP and real Postgres. Authorize/capture need the separate Mock Provider
 * service actually running, so those are exercised manually - see the M2
 * verification notes in CHANGELOG.md.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID organizationId;
    private UUID merchantId;
    private String apiKey;
    private String dashboardAccessToken;

    @BeforeEach
    void setUpOrganizationMerchantAndApiKey() {
        Tenant tenant = provisionTenant();
        organizationId = tenant.organizationId;
        merchantId = tenant.merchantId;
        apiKey = tenant.apiKey;
        dashboardAccessToken = tenant.accessToken;
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
                withApiKey(new CreatePaymentRequest(otherTenant.merchantId, new BigDecimal("50.00"), "USD", null, null)),
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
        headers.setBearerAuth(otherTenant.apiKey);
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/payments/" + created.id(), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PaymentResponse createPayment(BigDecimal amount) {
        return createPaymentFor(new Tenant(organizationId, merchantId, apiKey, dashboardAccessToken), amount);
    }

    private PaymentResponse createPaymentFor(Tenant tenant, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey);
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId, amount, "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Tenant provisionTenant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + suffix + "@example.com";
        String password = "correct-horse-battery-staple";

        CreateOrganizationResponse signup = restTemplate.postForEntity(
                "/v1/organizations", new CreateOrganizationRequest("Acme " + suffix, email, "Ada Owner", password),
                CreateOrganizationResponse.class).getBody();

        LoginResponse login = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(email, password), LoginResponse.class).getBody();

        HttpHeaders dashboardHeaders = new HttpHeaders();
        dashboardHeaders.setBearerAuth(login.accessToken());

        MerchantResponse merchant = restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/merchants", HttpMethod.POST,
                new HttpEntity<>(new CreateMerchantRequest("Test Merchant", "USD"), dashboardHeaders),
                MerchantResponse.class).getBody();

        restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/merchants/" + merchant.id() + "/provider-accounts",
                HttpMethod.POST,
                new HttpEntity<>(new CreateProviderAccountRequest(ProviderCode.MOCK, "{}", true), dashboardHeaders),
                Void.class);

        CreateApiKeyResponse apiKeyResponse = restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/api-keys", HttpMethod.POST,
                new HttpEntity<>(new CreateApiKeyRequest(ApiKeyEnvironment.TEST), dashboardHeaders),
                CreateApiKeyResponse.class).getBody();

        return new Tenant(signup.organizationId(), merchant.id(), apiKeyResponse.apiKey(), login.accessToken());
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

    private record Tenant(UUID organizationId, UUID merchantId, String apiKey, String accessToken) {
    }
}
