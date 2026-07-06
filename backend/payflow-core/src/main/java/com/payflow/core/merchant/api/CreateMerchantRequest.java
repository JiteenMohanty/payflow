package com.payflow.core.merchant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 3, max = 3) String defaultCurrency
) {
}
