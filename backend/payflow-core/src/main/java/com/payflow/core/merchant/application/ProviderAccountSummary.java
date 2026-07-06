package com.payflow.core.merchant.application;

import com.payflow.core.merchant.domain.ProviderAccountStatus;
import com.payflow.core.merchant.domain.ProviderCode;

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
