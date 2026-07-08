package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.infrastructure.web.CorrelationIdClientInterceptor;
import com.payflow.core.security.hmac.HmacSigner;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The shared sign+POST+interpret-response logic for one webhook delivery
 * attempt - used identically by WebhookDispatcher (M8, first attempt) and
 * WebhookRetryJob (M9, subsequent attempts), so a retried delivery is
 * indistinguishable, on the wire, from the original one. Never throws - a
 * provider-communication failure is reported as a failed DeliveryOutcome,
 * not an exception, so neither caller needs its own try/catch around this
 * call.
 */
@Component
public class WebhookDeliveryAttempter {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryAttempter.class);
    private static final String SIGNATURE_HEADER = "X-PayFlow-Signature";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final SymmetricEncryptor encryptor;
    private final HmacSigner hmacSigner;
    private final RestClient restClient;

    public WebhookDeliveryAttempter(
            RestClient.Builder restClientBuilder, CorrelationIdClientInterceptor correlationIdInterceptor,
            SymmetricEncryptor encryptor, HmacSigner hmacSigner) {
        this.encryptor = encryptor;
        this.hmacSigner = hmacSigner;
        this.restClient = buildRestClient(restClientBuilder, correlationIdInterceptor);
    }

    public DeliveryOutcome attempt(WebhookEndpoint endpoint, String payload) {
        String secret = new String(encryptor.decrypt(endpoint.getSecretEncrypted()), StandardCharsets.UTF_8);
        long timestampSeconds = System.currentTimeMillis() / 1000;
        String digest = hmacSigner.sign(secret, timestampSeconds + "." + payload);
        String signatureHeader = "t=" + timestampSeconds + ",v1=" + digest;

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(endpoint.getUrl())
                    .header(SIGNATURE_HEADER, signatureHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            // A 3xx never reaches here as a thrown exception - redirects are
            // never followed (see buildRestClient) - so it must be checked
            // explicitly rather than treating "no exception" as success.
            if (response.getStatusCode().is2xxSuccessful()) {
                return new DeliveryOutcome(true, response.getStatusCode().value());
            }
            return new DeliveryOutcome(false, response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            return new DeliveryOutcome(false, e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Webhook delivery failed for endpoint {}", endpoint.getId(), e);
            return new DeliveryOutcome(false, null);
        }
    }

    // Never follows redirects (EDD risk table: "outbound HTTP client denies
    // redirects to private ranges") - denying all redirects is a stricter
    // superset that doesn't need to inspect where a redirect points, since
    // it's never followed at all. Bounded connect/read timeouts so one slow
    // or hanging merchant endpoint can't tie up the calling thread
    // indefinitely.
    private RestClient buildRestClient(RestClient.Builder builder, CorrelationIdClientInterceptor correlationIdInterceptor) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.requestFactory(requestFactory).requestInterceptor(correlationIdInterceptor).build();
    }
}
