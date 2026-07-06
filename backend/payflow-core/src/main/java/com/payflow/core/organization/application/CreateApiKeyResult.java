package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.ApiKeyEnvironment;

import java.util.UUID;

public record CreateApiKeyResult(UUID id, String fullKey, String keyPrefix, ApiKeyEnvironment environment) {
}
