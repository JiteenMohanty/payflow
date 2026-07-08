package com.payflow.core.idempotency.persistence;

import com.payflow.core.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByOrganizationIdAndKey(UUID organizationId, String key);

    void deleteByOrganizationIdAndKey(UUID organizationId, String key);

    /**
     * Used by IdempotencyKeyCleanupJob (M9). expires_at, not created_at - a
     * row's whole reason to exist is the request-replay window described by
     * IdempotencyProperties.keyTtlHours (see the constructor), so once that
     * window has closed the row has no further purpose regardless of status.
     */
    long deleteByExpiresAtBefore(Instant cutoff);
}
