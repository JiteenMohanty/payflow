package com.payflow.mockprovider.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EDD section 5.3: "Every call has randomized latency (50-2000ms), a
 * configurable failure rate." Latency defaults to that real range (a
 * slower-but-correct response doesn't threaten test determinism), but
 * failureRate defaults to 0 - a nonzero default would make every prior
 * milestone's curl-based manual verification workflow flaky by default,
 * which is exactly what that workflow has depended on staying deterministic
 * to keep catching real bugs. Override via env vars for a dedicated chaos
 * exercise (see M11's manual verification).
 */
@ConfigurationProperties(prefix = "payflow.mock.chaos")
public record ChaosProperties(
        boolean latencyEnabled, long latencyMinMs, long latencyMaxMs,
        boolean failureEnabled, double failureRate) {
}
