package com.payflow.core.provider.mock;

import com.payflow.core.common.exception.ProviderCommunicationException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.provider.ProviderAuthorizationRequest;
import com.payflow.core.provider.ProviderAuthorizationResult;
import com.payflow.core.provider.ProviderAuthorizationStatus;
import com.payflow.core.provider.ProviderCaptureRequest;
import com.payflow.core.provider.ProviderCaptureResult;
import com.payflow.core.provider.ProviderCaptureStatus;
import com.payflow.core.provider.ProviderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the separate Mock Provider service over real HTTP, exactly as a
 * real provider integration would - see ADR-0006.
 */
@Component
public class MockProviderClient implements ProviderClient {

    private final RestClient restClient;

    public MockProviderClient(@Value("${payflow.provider.mock.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
}
