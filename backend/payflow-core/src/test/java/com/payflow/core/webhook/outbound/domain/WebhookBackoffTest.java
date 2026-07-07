package com.payflow.core.webhook.outbound.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookBackoffTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void firstAttemptDelaysAroundTheThirtySecondBaseDelay() {
        Instant next = WebhookBackoff.nextRetryAt(1, NOW);

        assertThat(next).isAfterOrEqualTo(NOW.plusSeconds(30));
        assertThat(next).isBefore(NOW.plusSeconds(37));
    }

    @Test
    void delayGrowsExponentiallyWithAttemptNumber() {
        Instant afterAttempt1 = WebhookBackoff.nextRetryAt(1, NOW);
        Instant afterAttempt4 = WebhookBackoff.nextRetryAt(4, NOW);

        // attempt 1 -> ~30s, attempt 4 -> ~240s; even with jitter on both
        // ends, 4 must clearly land later than 1.
        assertThat(afterAttempt4).isAfter(afterAttempt1.plusSeconds(120));
    }

    @Test
    void delayIsCappedAtOneHourEvenForManyAttempts() {
        Instant next = WebhookBackoff.nextRetryAt(50, NOW);

        assertThat(next).isBefore(NOW.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(15)));
    }

    @Test
    void neverProducesATimeBeforeNow() {
        Instant next = WebhookBackoff.nextRetryAt(0, NOW);

        assertThat(next).isAfterOrEqualTo(NOW.plusSeconds(30));
    }
}
