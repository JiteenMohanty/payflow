package com.payflow.core.outbox.application;

import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PENDING outbox_events row count as a live gauge - one of the business
 * metrics ADR-0012 names explicitly ("outbox backlog size"). A Micrometer
 * gauge is pull-based: the supplier below runs on each Prometheus scrape,
 * so no separate polling thread is needed here.
 */
@Component
@RequiredArgsConstructor
public class OutboxBacklogMetrics implements MeterBinder {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("payflow.outbox.backlog", outboxEventRepository,
                repo -> repo.countByStatus(OutboxEventStatus.PENDING));
    }
}
