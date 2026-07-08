package com.payflow.core.provider.mock;

import com.payflow.core.common.exception.ProviderCommunicationException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.infrastructure.web.CorrelationIdClientInterceptor;
import com.payflow.core.provider.ProviderAuthorizationRequest;
import com.payflow.core.provider.ProviderAuthorizationResult;
import com.payflow.core.provider.ProviderAuthorizationStatus;
import com.payflow.core.provider.ProviderCaptureRequest;
import com.payflow.core.provider.ProviderCaptureResult;
import com.payflow.core.provider.ProviderCaptureStatus;
import com.payflow.core.provider.ProviderChargeStatus;
import com.payflow.core.provider.ProviderChargeStatusResult;
import com.payflow.core.provider.ProviderClient;
import com.payflow.core.provider.ProviderRefundRequest;
import com.payflow.core.provider.ProviderRefundResult;
import com.payflow.core.provider.ProviderRefundStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to the separate Mock Provider service over real HTTP, exactly as a
 * real provider integration would - see ADR-0006. Built from the injected,
 * Spring Boot-autoconfigured RestClient.Builder (prototype-scoped, so each
 * injection point gets its own instance) rather than the static
 * RestClient.builder() factory - only the injected builder carries the
 * Micrometer HTTP client instrumentation (metrics + trace propagation,
 * ADR-0012) that Spring Boot wires onto it automatically.
 */
@Component
public class MockProviderClient implements ProviderClient {

    private final RestClient restClient;

    public MockProviderClient(
            RestClient.Builder restClientBuilder, CorrelationIdClientInterceptor correlationIdInterceptor,
            @Value("${payflow.provider.mock.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestInterceptor(correlationIdInterceptor).build();
    }

    @Override
    public ProviderCode providerCode() {
        return ProviderCode.MOCK;
    }

    @Override
    public ProviderAuthorizationResult authorize(ProviderAuthorizationRequest request) {
        try {
            MockChargeResponse response = restClient.post()
                    .uri("/provider/v1/charges")
                    .body(new MockChargeRequest(request.amount(), request.currency(), request.merchantReference()))
                    .retrieve()
                    .body(MockChargeResponse.class);

            ProviderAuthorizationStatus status = "AUTHORIZED".equals(response.status())
                    ? ProviderAuthorizationStatus.AUTHORIZED
                    : ProviderAuthorizationStatus.DECLINED;
            return new ProviderAuthorizationResult(status, response.chargeId(), response.declineReason());
        } catch (RestClientException e) {
            throw new ProviderCommunicationException("Mock provider authorize call failed", e);
        }
    }

    @Override
    public ProviderCaptureResult capture(ProviderCaptureRequest request) {
        try {
            MockCaptureResponse response = restClient.post()
                    .uri("/provider/v1/charges/{chargeId}/capture", request.providerReference())
                    .body(new MockCaptureRequest(request.amount(), request.currency()))
                    .retrieve()
                    .body(MockCaptureResponse.class);

            ProviderCaptureStatus status = "CAPTURED".equals(response.status())
                    ? ProviderCaptureStatus.CAPTURED
                    : ProviderCaptureStatus.FAILED;
            return new ProviderCaptureResult(status, response.failureReason());
        } catch (RestClientException e) {
            throw new ProviderCommunicationException("Mock provider capture call failed", e);
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest request) {
        try {
            MockRefundResponse response = restClient.post()
                    .uri("/provider/v1/charges/{chargeId}/refund", request.providerReference())
                    .body(new MockRefundRequest(request.amount(), request.currency()))
                    .retrieve()
                    .body(MockRefundResponse.class);

            ProviderRefundStatus status = "REFUNDED".equals(response.status())
                    ? ProviderRefundStatus.REFUNDED
                    : ProviderRefundStatus.FAILED;
            return new ProviderRefundResult(status, response.failureReason());
        } catch (RestClientException e) {
            throw new ProviderCommunicationException("Mock provider refund call failed", e);
        }
    }

    @Override
    public ProviderChargeStatusResult checkStatus(String providerReference) {
        try {
            MockChargeStatusResponse response = restClient.get()
                    .uri("/provider/v1/charges/{chargeId}", providerReference)
                    .retrieve()
                    .body(MockChargeStatusResponse.class);

            ProviderChargeStatus status = "CAPTURED".equals(response.status())
                    ? ProviderChargeStatus.CAPTURED
                    : ProviderChargeStatus.AUTHORIZED;
            return new ProviderChargeStatusResult(status, response.amount(), response.currency());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new ProviderChargeStatusResult(ProviderChargeStatus.NOT_FOUND, null, null);
            }
            throw new ProviderCommunicationException("Mock provider status check failed", e);
        } catch (RestClientException e) {
            throw new ProviderCommunicationException("Mock provider status check failed", e);
        }
    }
}
