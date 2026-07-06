package com.payflow.core.merchant.application;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.merchant.domain.ProviderAccountStatus;

import java.time.Instant;
import java.util.UUID;

public record ProviderAccountSummary(
        UUID id,
        UUID merchantId,
        ProviderCode providerCode,
        boolean isDefault,
        ProviderAccountStatus status,
        Instant createdAt
) {
}
