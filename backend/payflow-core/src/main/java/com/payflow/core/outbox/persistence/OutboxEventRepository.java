package com.payflow.core.outbox.persistence;

import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    /**
     * FOR UPDATE SKIP LOCKED lets multiple poller instances run concurrently
     * against the same table without blocking each other on the same rows -
     * see ADR-0005 and EDD section 7.4. Must be called within an active
     * transaction so the locks are actually held for the caller's duration.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' "
            + "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit);

    /**
     * Used by OutboxCleanupJob (M9). Scoped to PUBLISHED only - a PENDING row
     * old enough to match the cutoff is a stuck event an operator needs to
     * see, not something to silently delete.
     */
    long deleteByStatusAndCreatedAtBefore(OutboxEventStatus status, Instant cutoff);

    /**
     * Backs the payflow.outbox.backlog gauge (OutboxBacklogMetrics, M10,
     * ADR-0012) - a growing PENDING count is the outbox-pattern equivalent
     * of a stuck queue, and one of the business metrics the ADR names
     * explicitly.
     */
    long countByStatus(OutboxEventStatus status);
}
