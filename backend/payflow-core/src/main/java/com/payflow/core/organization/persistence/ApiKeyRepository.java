package com.payflow.core.organization.persistence;

import com.payflow.core.organization.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    List<ApiKey> findByOrganizationId(UUID organizationId);
}
