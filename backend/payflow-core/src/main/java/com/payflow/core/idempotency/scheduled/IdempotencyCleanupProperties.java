package com.payflow.core.idempotency.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.idempotency-cleanup")
public record IdempotencyCleanupProperties(String cron) {
}
