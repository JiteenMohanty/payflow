package com.payflow.core.merchant.application;

import com.payflow.core.merchant.domain.MerchantStatus;

import java.util.UUID;

public record MerchantSummary(UUID id, UUID organizationId, String name, String defaultCurrency, MerchantStatus status) {
}
