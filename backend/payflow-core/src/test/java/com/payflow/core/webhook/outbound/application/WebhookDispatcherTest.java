package com.payflow.core.webhook.outbound.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.common.event.PaymentEventPayload;
import com.payflow.core.security.hmac.HmacSigner;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private WebhookEndpointRepository endpointRepository;
    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private SymmetricEncryptor encryptor;
    @Mock
    private HmacSigner hmacSigner;

    private WebhookDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookDispatcher(endpointRepository, deliveryRepository, encryptor, hmacSigner, objectMapper);
    }

    @Test
    void deliversToASubscribedActiveEndpointAndRecordsFailureWhenUnreachable() {
        WebhookEndpoint endpoint = newEndpoint(unreachableUrl(), List.of("payment.captured"));
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(encryptor.decrypt(any())).thenReturn("secret".getBytes(StandardCharsets.UTF_8));
        when(hmacSigner.sign(any(), any())).thenReturn("deadbeef");
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.onMessage(paymentPayload("payment.captured"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        WebhookDelivery finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(finalState.getNextRetryAt()).isNotNull();
        assertThat(finalState.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void skipsAnEndpointNotSubscribedToTheEventType() {
        WebhookEndpoint endpoint = newEndpoint(unreachableUrl(), List.of("payment.authorized"));
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));

        dispatcher.onMessage(paymentPayload("payment.captured"));

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void skipsWhenNoEndpointsAreRegisteredForTheOrganization() {
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of());

        dispatcher.onMessage(paymentPayload("payment.captured"));

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void skipsWhenTheEventEnvelopeCannotBeParsed() {
        dispatcher.onMessage("not valid json");

        verify(endpointRepository, never()).findByOrganizationIdAndStatus(any(), any());
    }

    private WebhookEndpoint newEndpoint(String url, List<String> subscribedEvents) {
        WebhookEndpoint endpoint = new WebhookEndpoint(organizationId, url, new byte[]{1}, subscribedEvents);
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        return endpoint;
    }

    private String paymentPayload(String eventType) {
        try {
            return objectMapper.writeValueAsString(new PaymentEventPayload(
                    eventType, UUID.randomUUID(), organizationId, UUID.randomUUID(), "CAPTURED",
                    new BigDecimal("10.00"), "USD", Instant.now()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
