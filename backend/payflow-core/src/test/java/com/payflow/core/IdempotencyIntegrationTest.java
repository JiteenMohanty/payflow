package com.payflow.core;

import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentResponse;
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
 * Exercises the Idempotency-Key mechanism over real HTTP, real Postgres, and
 * real Redis - see ADR-0007. Scoped to API-key authenticated requests only.
 */
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    private UUID merchantId;
    private String apiKey;

    @BeforeEach
    void setUp() {
        Tenant tenant = provisionTenant();
        merchantId = tenant.merchantId();
        apiKey = tenant.apiKey();
    }

    @Test
    void sameKeyAndBodyReplaysTheOriginalResponseWithoutCreatingASecondPayment() {
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest(merchantId, new BigDecimal("25.00"), "USD", "Order A", null);

        ResponseEntity<PaymentResponse> first = post(idempotencyKey, request);
        ResponseEntity<PaymentResponse> second = post(idempotencyKey, request);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        ResponseEntity<PaymentResponse[]> list = restTemplate.exchange(
                "/v1/payments?merchantId=" + merchantId, HttpMethod.GET, withApiKey(), PaymentResponse[].class);
        assertThat(list.getBody()).hasSize(1);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() {
        String idempotencyKey = UUID.randomUUID().toString();
        post(idempotencyKey, new CreatePaymentRequest(merchantId, new BigDecimal("25.00"), "USD", "Order A", null));

        ResponseEntity<String> second = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST,
                withApiKeyAndIdempotencyKey(idempotencyKey,
                        new CreatePaymentRequest(merchantId, new BigDecimal("99.00"), "USD", "Order B", null)),
                String.class);

        assertThat(second.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void withoutAnIdempotencyKeyEachRequestIsIndependent() {
        CreatePaymentRequest request = new CreatePaymentRequest(merchantId, new BigDecimal("15.00"), "USD", null, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);

        ResponseEntity<PaymentResponse> first = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST, new HttpEntity<>(request, headers), PaymentResponse.class);
        ResponseEntity<PaymentResponse> second = restTemplate.exchange(
                "/v1/payments", HttpMethod.POST, new HttpEntity<>(request, headers), PaymentResponse.class);

        assertThat(first.getBody().id()).isNotEqualTo(second.getBody().id());
    }

    private ResponseEntity<PaymentResponse> post(String idempotencyKey, CreatePaymentRequest request) {
        return restTemplate.exchange(
                "/v1/payments", HttpMethod.POST, withApiKeyAndIdempotencyKey(idempotencyKey, request), PaymentResponse.class);
    }

    private HttpEntity<CreatePaymentRequest> withApiKeyAndIdempotencyKey(String idempotencyKey, CreatePaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.set("Idempotency-Key", idempotencyKey);
        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<Void> withApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return new HttpEntity<>(headers);
    }
}
