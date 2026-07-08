package com.payflow.core.outbox.scheduled;

import com.payflow.core.infrastructure.web.ScheduledJobCorrelation;
import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes PUBLISHED outbox_events rows past the retention window, keeping
 * the table from growing unbounded once a deployment has been live for a
 * while. PENDING rows are never touched: only OutboxPublisher transitions a
 * row out of PENDING, so a PENDING row old enough to match this job's cutoff
 * is a stuck event an operator needs to see, not clean up silently.
 */
@Component
@RequiredArgsConstructor
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxCleanupProperties properties;

    @Scheduled(cron = "${payflow.outbox-cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> {
            Instant cutoff = Instant.now().minus(Duration.ofDays(properties.retentionDays()));
            long deleted = outboxEventRepository.deleteByStatusAndCreatedAtBefore(OutboxEventStatus.PUBLISHED, cutoff);
            if (deleted > 0) {
                log.info("Outbox cleanup deleted {} published event(s) older than {} day(s)", deleted, properties.retentionDays());
            }
        });
    }
}
