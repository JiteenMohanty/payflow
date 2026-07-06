package com.payflow.core.organization.api;

import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.organization.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String keyPrefix,
        ApiKeyEnvironment environment,
        ApiKeyStatus status,
        Instant lastUsedAt,
        Instant createdAt
) {
}
