package com.payflow.core.webhook.outbound.application;

/**
 * responseCode is null when the attempt never got an HTTP response at all
 * (connection failure, timeout) - distinct from a non-2xx response, which
 * carries the code the merchant endpoint actually returned.
 */
public record DeliveryOutcome(boolean succeeded, Integer responseCode) {
}
