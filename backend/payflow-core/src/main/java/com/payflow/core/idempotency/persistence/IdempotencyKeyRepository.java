package com.payflow.core.idempotency.persistence;

import com.payflow.core.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByOrganizationIdAndKey(UUID organizationId, String key);

    void deleteByOrganizationIdAndKey(UUID organizationId, String key);
}
