package com.payflow.core.idempotency.application;

public sealed interface IdempotencyCheckResult {

    record Proceed() implements IdempotencyCheckResult {
    }

    record Replay(int statusCode, String responseBody) implements IdempotencyCheckResult {
    }

    record InProgress() implements IdempotencyCheckResult {
    }

    record FingerprintMismatch() implements IdempotencyCheckResult {
    }
}
