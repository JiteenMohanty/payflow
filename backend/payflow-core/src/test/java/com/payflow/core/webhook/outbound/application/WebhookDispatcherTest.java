package com.payflow.core.webhook.outbound.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.event.PaymentEventPayload;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
    private WebhookDeliveryAttempter deliveryAttempter;

    private WebhookDispatcher dispatcher;
    private SimpleMeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new WebhookDispatcher(endpointRepository, deliveryRepository, deliveryAttempter, objectMapper, meterRegistry);
    }

    @Test
    void deliversToASubscribedActiveEndpointAndRecordsFailureWhenTheAttemptFails() {
        WebhookEndpoint endpoint = newEndpoint(List.of("payment.captured"));
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(deliveryAttempter.attempt(any(), any())).thenReturn(new DeliveryOutcome(false, null));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.onMessage(paymentPayload("payment.captured"), "test-correlation-id");

        WebhookDelivery finalState = lastSaved();
        assertThat(finalState.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(finalState.getNextRetryAt()).isNotNull();
        assertThat(finalState.getAttemptNumber()).isEqualTo(1);
        assertThat(meterRegistry.get(DeliveryOutcome.DELIVERIES_METRIC).tag("outcome", "failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void deliversToASubscribedActiveEndpointAndRecordsSuccess() {
        WebhookEndpoint endpoint = newEndpoint(List.of("payment.captured"));
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(deliveryAttempter.attempt(any(), any())).thenReturn(new DeliveryOutcome(true, 200));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.onMessage(paymentPayload("payment.captured"), "test-correlation-id");

        WebhookDelivery finalState = lastSaved();
        assertThat(finalState.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(finalState.getResponseCode()).isEqualTo(200);
    }

    @Test
    void skipsAnEndpointNotSubscribedToTheEventType() {
        WebhookEndpoint endpoint = newEndpoint(List.of("payment.authorized"));
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));

        dispatcher.onMessage(paymentPayload("payment.captured"), "test-correlation-id");

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void skipsWhenNoEndpointsAreRegisteredForTheOrganization() {
        when(endpointRepository.findByOrganizationIdAndStatus(organizationId, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of());

        dispatcher.onMessage(paymentPayload("payment.captured"), "test-correlation-id");

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void skipsWhenTheEventEnvelopeCannotBeParsed() {
        dispatcher.onMessage("not valid json", "test-correlation-id");

        verify(endpointRepository, never()).findByOrganizationIdAndStatus(any(), any());
    }

    private WebhookDelivery lastSaved() {
        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    private WebhookEndpoint newEndpoint(List<String> subscribedEvents) {
        WebhookEndpoint endpoint = new WebhookEndpoint(organizationId, "http://example.invalid", new byte[]{1}, subscribedEvents);
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
}
