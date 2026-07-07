package com.payflow.core;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.outbox.application.OutboxPublisher;
import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentResponse;
import com.payflow.core.security.hmac.HmacSha256Signer;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real webhook delivery end-to-end: a payment.created event flows
 * through the outbox, Kafka, and WebhookDispatcher's real @KafkaListener to
 * a local HTTP server, which independently verifies the delivered
 * signature. The endpoint is inserted directly via the repository, not
 * through POST /v1/webhook-endpoints - WebhookUrlValidator correctly
 * rejects a loopback URL at registration (that's the whole point of it),
 * so a real localhost test target can only be wired in this way.
 */
class WebhookDeliveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookEndpointRepository webhookEndpointRepository;
    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;
    @Autowired
    private SymmetricEncryptor encryptor;
    @Autowired
    private OutboxPublisher outboxPublisher;

    private HttpServer receiver;

    @AfterEach
    void tearDown() {
        if (receiver != null) {
            receiver.stop(0);
        }
    }

    @Test
    void aPaymentCreatedEventIsDeliveredWithAVerifiableSignature() throws IOException, InterruptedException {
        String secret = "whsec_test_secret_for_delivery_verification";
        BlockingQueue<ReceivedRequest> received = new ArrayBlockingQueue<>(1);
        int port = startReceiver(received);

        Tenant tenant = provisionTenant();
        WebhookEndpoint endpoint = new WebhookEndpoint(
                tenant.organizationId(), "http://127.0.0.1:" + port + "/hooks",
                encryptor.encrypt(secret.getBytes(StandardCharsets.UTF_8)), List.of("payment.created"));
        webhookEndpointRepository.save(endpoint);

        UUID paymentId = createPayment(tenant);
        outboxPublisher.relayPendingEvents();

        ReceivedRequest request = received.poll(20, TimeUnit.SECONDS);
        assertThat(request).as("webhook delivery never reached the local receiver").isNotNull();
        assertThat(request.body()).contains("\"eventType\":\"payment.created\"");
        assertThat(request.body()).contains(paymentId.toString());

        String[] parts = request.signatureHeader().split(",");
        String timestamp = parts[0].substring(2);
        String providedDigest = parts[1].substring(3);
        String expectedDigest = new HmacSha256Signer().sign(secret, timestamp + "." + request.body());
        assertThat(providedDigest).isEqualTo(expectedDigest);

        for (int i = 0; i < 20; i++) {
            List<WebhookDelivery> deliveries = webhookDeliveryRepository.findByWebhookEndpointIdOrderByCreatedAtDesc(endpoint.getId());
            if (!deliveries.isEmpty() && deliveries.get(0).getStatus() == WebhookDeliveryStatus.SUCCEEDED) {
                assertThat(deliveries.get(0).getResponseCode()).isEqualTo(200);
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("webhook_deliveries row never reached SUCCEEDED");
    }

    private int startReceiver(BlockingQueue<ReceivedRequest> received) throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hooks", exchange -> {
            String signature = exchange.getRequestHeaders().getFirst("X-PayFlow-Signature");
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            received.offer(new ReceivedRequest(signature, body));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        return receiver.getAddress().getPort();
    }

    private UUID createPayment(Tenant tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId(), new BigDecimal("1.00"), "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        return response.getBody().id();
    }

    private record ReceivedRequest(String signatureHeader, String body) {
    }
}
