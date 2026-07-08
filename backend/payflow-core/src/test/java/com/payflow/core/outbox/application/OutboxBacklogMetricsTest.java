package com.payflow.core.outbox.application;

import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxBacklogMetricsTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void gaugeReflectsTheCurrentPendingCountOnEachRead() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxBacklogMetrics(outboxEventRepository).bindTo(registry);

        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(7L);
        assertThat(registry.get("payflow.outbox.backlog").gauge().value()).isEqualTo(7.0);

        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(3L);
        assertThat(registry.get("payflow.outbox.backlog").gauge().value()).isEqualTo(3.0);
    }
}
