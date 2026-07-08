package com.payflow.core.webhook.outbound.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.webhook-retry")
public record WebhookRetryProperties(long pollIntervalMs, int batchSize, int maxAttempts) {
}
