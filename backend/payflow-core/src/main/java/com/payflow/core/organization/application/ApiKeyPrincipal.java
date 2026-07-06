package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.ApiKeyEnvironment;

import java.util.UUID;

public record ApiKeyPrincipal(UUID apiKeyId, UUID organizationId, ApiKeyEnvironment environment) {
}
