package com.payflow.core.merchant.api;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.merchant.domain.ProviderAccountStatus;

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
