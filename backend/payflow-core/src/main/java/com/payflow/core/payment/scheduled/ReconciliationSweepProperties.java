package com.payflow.core.payment.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.reconciliation")
public record ReconciliationSweepProperties(long sweepIntervalMs, long staleWindowMinutes) {
}
