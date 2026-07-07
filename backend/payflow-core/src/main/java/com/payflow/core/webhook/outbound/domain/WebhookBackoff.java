package com.payflow.core.webhook.outbound.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter for webhook_deliveries.next_retry_at
 * (EDD section 7.5, risk table: "Exponential backoff with jitter on ...
 * webhook delivery retries"). Pure and Spring-free, like
 * PaymentStateMachine, so it's directly unit-testable. Computes the
 * schedule only - WebhookRetryJob (M9) is what actually consumes
 * next_retry_at to drive a retry.
 */
public final class WebhookBackoff {

    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofHours(1);
    private static final int MAX_SHIFT = 20;
    private static final double JITTER_FRACTION = 0.2;

    private WebhookBackoff() {
    }

    public static Instant nextRetryAt(int attemptNumber) {
        return nextRetryAt(attemptNumber, Instant.now());
    }

    static Instant nextRetryAt(int attemptNumber, Instant now) {
        int shift = Math.min(Math.max(attemptNumber - 1, 0), MAX_SHIFT);
        long uncappedMillis = BASE_DELAY.toMillis() * (1L << shift);
        long delayMillis = Math.min(uncappedMillis, MAX_DELAY.toMillis());
        long jitterMillis = (long) (delayMillis * JITTER_FRACTION * ThreadLocalRandom.current().nextDouble());
        return now.plusMillis(delayMillis + jitterMillis);
    }
}
