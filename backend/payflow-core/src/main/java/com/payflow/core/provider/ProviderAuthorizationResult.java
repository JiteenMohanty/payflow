package com.payflow.core.provider;

public record ProviderAuthorizationResult(ProviderAuthorizationStatus status, String providerReference, String failureReason) {
}
