package com.payflow.core.merchant.application;

import java.util.List;
import java.util.UUID;

public interface MerchantLookupService {

    MerchantSummary getById(UUID merchantId);

    List<MerchantSummary> listByOrganization(UUID organizationId);
}
