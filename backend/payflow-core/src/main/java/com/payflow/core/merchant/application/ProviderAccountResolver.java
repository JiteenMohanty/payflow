package com.payflow.core.merchant.application;

import java.util.UUID;

/**
 * Consumed by the payment module (from M2 onward) to determine which
 * provider account a payment should route through.
 */
public interface ProviderAccountResolver {

    ProviderAccountSummary resolveDefault(UUID merchantId);
}
