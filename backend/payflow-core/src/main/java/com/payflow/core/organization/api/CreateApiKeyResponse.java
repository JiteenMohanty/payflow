package com.payflow.core.organization.api;

import com.payflow.core.organization.domain.ApiKeyEnvironment;

import java.util.UUID;

public record CreateApiKeyResponse(UUID id, String apiKey, String keyPrefix, ApiKeyEnvironment environment) {
}
