package com.payflow.core.merchant.api;

import com.payflow.core.merchant.domain.MerchantStatus;

import java.util.UUID;

public record MerchantResponse(UUID id, UUID organizationId, String name, String defaultCurrency, MerchantStatus status) {
}
