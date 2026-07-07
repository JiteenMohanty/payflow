package com.payflow.core.outbox.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.outbox")
public record OutboxProperties(long pollIntervalMs, int batchSize, int maxRetries) {
}
