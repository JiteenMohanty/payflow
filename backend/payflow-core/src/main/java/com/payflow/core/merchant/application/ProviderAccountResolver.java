package com.payflow.core.merchant.application;

import java.util.UUID;

/**
 * Consumed by the payment module to determine which provider account a
 * payment should route through.
 */
public interface ProviderAccountResolver {

    ProviderAccountSummary resolveDefault(UUID merchantId);

    ProviderAccountSummary resolveById(UUID merchantId, UUID providerAccountId);
}
