package com.payflow.core.provider;

public record ProviderCaptureResult(ProviderCaptureStatus status, String failureReason) {
}
