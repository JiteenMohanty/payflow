package com.payflow.core.organization.api;

import com.payflow.core.organization.domain.ApiKeyEnvironment;
import jakarta.validation.constraints.NotNull;

public record CreateApiKeyRequest(@NotNull ApiKeyEnvironment environment) {
}
