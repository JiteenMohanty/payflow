package com.payflow.core.merchant.application;

import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.merchant.domain.Merchant;
import com.payflow.core.merchant.persistence.MerchantRepository;
import com.payflow.core.organization.application.OrganizationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService implements MerchantLookupService {

    private final MerchantRepository merchantRepository;
    private final OrganizationLookupService organizationLookupService;

    @Transactional
    public MerchantSummary createMerchant(UUID organizationId, String name, String defaultCurrency) {
        organizationLookupService.getById(organizationId);

        Merchant merchant = new Merchant(organizationId, name, defaultCurrency.toUpperCase());
        merchantRepository.save(merchant);
        return toSummary(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantSummary getById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(this::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchantSummary> listByOrganization(UUID organizationId) {
        return merchantRepository.findByOrganizationId(organizationId).stream()
                .map(this::toSummary)
                .toList();
    }

    Merchant getEntityWithinOrganization(UUID merchantId, UUID organizationId) {
        return merchantRepository.findByIdAndOrganizationId(merchantId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));
    }

    private MerchantSummary toSummary(Merchant merchant) {
        return new MerchantSummary(
                merchant.getId(), merchant.getOrganizationId(), merchant.getName(),
                merchant.getDefaultCurrency(), merchant.getStatus());
    }
}
