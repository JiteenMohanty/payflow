package com.payflow.core.merchant.api;

import com.payflow.core.common.provider.ProviderCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProviderAccountRequest(
        @NotNull ProviderCode providerCode,
        @NotBlank String credentialsJson,
        boolean isDefault
) {
}
