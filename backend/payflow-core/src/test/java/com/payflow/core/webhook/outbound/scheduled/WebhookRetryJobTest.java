package com.payflow.core.webhook.outbound.scheduled;

import com.payflow.core.outbox.application.DeadLetterWriter;
import com.payflow.core.webhook.outbound.application.DeliveryOutcome;
import com.payflow.core.webhook.outbound.application.WebhookDeliveryAttempter;
import com.payflow.core.webhook.outbound.domain.WebhookDelivery;
import com.payflow.core.webhook.outbound.domain.WebhookDeliveryStatus;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRetryJobTest {

    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private WebhookDeliveryAttempter deliveryAttempter;
    @Mock
    private DeadLetterWriter deadLetterWriter;

    private WebhookRetryJob job;

    @BeforeEach
    void setUp() {
        job = new WebhookRetryJob(deliveryRepository, deliveryAttempter, deadLetterWriter,
                new WebhookRetryProperties(30_000, 100, 6));
    }

    @Test
    void aSuccessfulRetryMarksTheDeliverySucceeded() {
        WebhookDelivery delivery = failedDelivery(1);
        when(deliveryRepository.lockNextRetryBatch(100)).thenReturn(List.of(delivery));
        when(deliveryAttempter.attempt(eq(delivery.getWebhookEndpoint()), eq(delivery.getPayload())))
                .thenReturn(new DeliveryOutcome(true, 200));

        job.retryFailedDeliveries();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        verify(deadLetterWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void aFailedRetryBelowMaxAttemptsReschedulesWithAnIncrementedAttemptNumber() {
        WebhookDelivery delivery = failedDelivery(1);
        when(deliveryRepository.lockNextRetryBatch(100)).thenReturn(List.of(delivery));
        when(deliveryAttempter.attempt(any(), any())).thenReturn(new DeliveryOutcome(false, 500));

        job.retryFailedDeliveries();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptNumber()).isEqualTo(2);
        assertThat(delivery.getNextRetryAt()).isNotNull();
        verify(deadLetterWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void aFailedRetryAtMaxAttemptsMarksExhaustedAndDeadLetters() {
        WebhookDelivery delivery = failedDelivery(5);
        when(deliveryRepository.lockNextRetryBatch(100)).thenReturn(List.of(delivery));
        when(deliveryAttempter.attempt(any(), any())).thenReturn(new DeliveryOutcome(false, 500));

        job.retryFailedDeliveries();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.EXHAUSTED);
        assertThat(delivery.getAttemptNumber()).isEqualTo(6);
        assertThat(delivery.getNextRetryAt()).isNull();
        verify(deadLetterWriter).write(eq("webhook_delivery"), eq(delivery.getId()), eq(delivery.getPayload()), anyString());
    }

    @Test
    void aDisabledEndpointPausesRetryWithoutAttemptingDeliveryOrDeadLettering() {
        WebhookDelivery delivery = failedDelivery(1);
        delivery.getWebhookEndpoint().disable();
        when(deliveryRepository.lockNextRetryBatch(100)).thenReturn(List.of(delivery));

        job.retryFailedDeliveries();

        assertThat(delivery.getNextRetryAt()).isNull();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        verify(deliveryAttempter, never()).attempt(any(), any());
        verify(deadLetterWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    void oneFailingDeliveryDoesNotStopTheRestOfTheBatch() {
        WebhookDelivery throwing = failedDelivery(1);
        WebhookDelivery ok = failedDelivery(1);
        when(deliveryRepository.lockNextRetryBatch(100)).thenReturn(List.of(throwing, ok));
        when(deliveryAttempter.attempt(eq(throwing.getWebhookEndpoint()), any())).thenThrow(new RuntimeException("boom"));
        when(deliveryAttempter.attempt(eq(ok.getWebhookEndpoint()), any())).thenReturn(new DeliveryOutcome(true, 200));

        job.retryFailedDeliveries();

        assertThat(ok.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
    }

    private WebhookDelivery failedDelivery(int attemptNumber) {
        WebhookEndpoint endpoint = new WebhookEndpoint(UUID.randomUUID(), "https://merchant.example/webhooks",
                new byte[]{1}, List.of("payment.captured"));
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        WebhookDelivery delivery = new WebhookDelivery(endpoint, "payment.captured", "{}");
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "attemptNumber", attemptNumber);
        delivery.markFailed(500, Instant.now().minusSeconds(1));
        return delivery;
    }
}
