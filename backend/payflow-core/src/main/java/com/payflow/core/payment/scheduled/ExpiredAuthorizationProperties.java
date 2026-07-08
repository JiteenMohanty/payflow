package com.payflow.core.payment.scheduled;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.expired-authorization")
public record ExpiredAuthorizationProperties(long sweepIntervalMs, long authWindowHours) {
}
