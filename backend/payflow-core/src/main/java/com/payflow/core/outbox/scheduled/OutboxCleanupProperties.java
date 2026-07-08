package com.payflow.core.outbox.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.outbox-cleanup")
public record OutboxCleanupProperties(String cron, long retentionDays) {
}
