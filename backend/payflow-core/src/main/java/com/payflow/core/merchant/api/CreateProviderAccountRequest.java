package com.payflow.core.merchant.api;

import com.payflow.core.merchant.domain.ProviderCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProviderAccountRequest(
        @NotNull ProviderCode providerCode,
        @NotBlank String credentialsJson,
        boolean isDefault
) {
}
