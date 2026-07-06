package com.payflow.core.idempotency.application;

import java.util.UUID;

/**
 * Consumed by {@code IdempotencyFilter}, which wraps every API-key
 * authenticated mutating request carrying an {@code Idempotency-Key} header -
 * see ADR-0007.
 */
public interface IdempotencyGuard {

    IdempotencyCheckResult check(UUID organizationId, String key, String requestFingerprint);

    void complete(UUID organizationId, String key, String requestFingerprint, int statusCode, String responseBody);

    void abandon(UUID organizationId, String key);
}
