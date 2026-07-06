package com.payflow.core.merchant.api;

import com.payflow.core.merchant.domain.ProviderAccountStatus;
import com.payflow.core.merchant.domain.ProviderCode;

import java.time.Instant;
import java.util.UUID;

public record ProviderAccountResponse(
        UUID id,
        UUID merchantId,
        ProviderCode providerCode,
        boolean isDefault,
        ProviderAccountStatus status,
        Instant createdAt
) {
}
