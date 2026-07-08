package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.infrastructure.web.CorrelationIdClientInterceptor;
import com.payflow.core.security.hmac.HmacSigner;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryAttempterTest {

    @Mock
    private SymmetricEncryptor encryptor;
    @Mock
    private HmacSigner hmacSigner;

    private WebhookDeliveryAttempter attempter;
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        attempter = new WebhookDeliveryAttempter(RestClient.builder(), new CorrelationIdClientInterceptor(), encryptor, hmacSigner);
    }

    @Test
    void reportsAFailedOutcomeWithNoResponseCodeWhenTheEndpointIsUnreachable() {
        WebhookEndpoint endpoint = newEndpoint(unreachableUrl());
        when(encryptor.decrypt(any())).thenReturn("secret".getBytes(StandardCharsets.UTF_8));
        when(hmacSigner.sign(any(), any())).thenReturn("deadbeef");

        DeliveryOutcome outcome = attempter.attempt(endpoint, "{}");

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.responseCode()).isNull();
    }

    private WebhookEndpoint newEndpoint(String url) {
        WebhookEndpoint endpoint = new WebhookEndpoint(organizationId, url, new byte[]{1}, List.of("payment.captured"));
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        return endpoint;
    }

    /**
     * Opens then immediately closes a socket on an ephemeral port, so a
     * connection attempt to it is deterministically refused - no live
     * server needed to exercise the delivery-failure path.
     */
    private String unreachableUrl() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
