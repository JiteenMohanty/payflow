package com.payflow.core.idempotency.scheduled;

import com.payflow.core.idempotency.persistence.IdempotencyKeyRepository;
import com.payflow.core.infrastructure.web.ScheduledJobCorrelation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes idempotency_keys rows past their own expires_at - unlike
 * OutboxCleanupJob's retention window, no separate window is needed here:
 * expires_at already IS the row's whole reason to exist (the request-replay
 * guarantee IdempotencyProperties.keyTtlHours describes), so anything past
 * it, IN_PROGRESS or COMPLETED, has no further purpose.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Scheduled(cron = "${payflow.idempotency-cleanup.cron:0 30 3 * * *}")
    @Transactional
    public void cleanup() {
        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> {
            long deleted = idempotencyKeyRepository.deleteByExpiresAtBefore(Instant.now());
            if (deleted > 0) {
                log.info("Idempotency key cleanup deleted {} expired key(s)", deleted);
            }
        });
    }
}
