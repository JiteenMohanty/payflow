package com.payflow.core.idempotency.application;

/**
 * The Redis-cached form of a completed idempotent response. The fingerprint
 * travels with it so a cache hit can still detect key reuse with a different
 * request body without needing to fall back to the database.
 */
public record CachedIdempotentResponse(String fingerprint, int statusCode, String responseBody) {
}
