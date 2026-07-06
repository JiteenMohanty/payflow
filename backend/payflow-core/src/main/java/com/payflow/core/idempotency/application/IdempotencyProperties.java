package com.payflow.core.idempotency.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.idempotency")
public record IdempotencyProperties(long keyTtlHours, long redisCacheTtlMinutes) {
}
