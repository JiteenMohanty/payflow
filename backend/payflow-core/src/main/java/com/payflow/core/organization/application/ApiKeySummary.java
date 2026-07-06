package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.organization.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record ApiKeySummary(
        UUID id,
        String keyPrefix,
        ApiKeyEnvironment environment,
        ApiKeyStatus status,
        Instant lastUsedAt,
        Instant createdAt
) {
}
