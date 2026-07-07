package com.payflow.core.outbox.application;

import com.payflow.core.outbox.domain.DeadLetterEvent;
import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.DeadLetterEventRepository;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DeadLetterEventRepository deadLetterEventRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(500, 100, 3);
        publisher = new OutboxPublisher(outboxEventRepository, deadLetterEventRepository, kafkaTemplate, properties);
    }

    @Test
    void successfullyPublishedEventsAreMarkedPublished() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.lockNextBatch(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(deadLetterEventRepository, never()).save(any());
    }

    @Test
    void aFailedSendIncrementsRetryCountWithoutMovingToDeadLetterBelowTheThreshold() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.lockNextBatch(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        publisher.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(deadLetterEventRepository, never()).save(any());
        verify(outboxEventRepository, never()).delete(any());
    }

    @Test
    void exceedingMaxRetriesMovesTheEventToDeadLetterAndRemovesItFromTheOutbox() {
        OutboxEvent event = pendingEvent();
        ReflectionTestUtils.setField(event, "retryCount", 2);
        when(outboxEventRepository.lockNextBatch(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        publisher.relayPendingEvents();

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceId()).isEqualTo(event.getAggregateId());
        assertThat(captor.getValue().getErrorReason()).contains("broker unavailable");
        verify(outboxEventRepository).delete(event);
    }

    @Test
    void aNullExceptionMessageStillProducesAUsefulErrorReason() {
        // java.util.concurrent.TimeoutException from Future.get(timeout) -
        // the actual exception a real Kafka outage produces via publishOne's
        // .get(SEND_TIMEOUT_SECONDS, ...) call when the future is still
        // incomplete at the deadline - has a null getMessage() by
        // construction. Found by manual verification: a genuine Kafka outage
        // left dead_letter_events.error_reason empty. Simulated here via a
        // synchronous throw (a null-message exception thrown before the
        // future/get() machinery is even involved) rather than trying to
        // force a real timeout in a unit test.
        OutboxEvent event = pendingEvent();
        ReflectionTestUtils.setField(event, "retryCount", 4);
        when(outboxEventRepository.lockNextBatch(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException());

        publisher.relayPendingEvents();

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterEventRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorReason()).isEqualTo("IllegalStateException");
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent("PAYMENT", UUID.randomUUID(), "payment.created", "payflow.payments", "{}");
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }
}
