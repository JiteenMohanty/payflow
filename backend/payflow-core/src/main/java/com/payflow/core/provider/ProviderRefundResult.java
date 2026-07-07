package com.payflow.core.provider;

public record ProviderRefundResult(ProviderRefundStatus status, String failureReason) {
}
