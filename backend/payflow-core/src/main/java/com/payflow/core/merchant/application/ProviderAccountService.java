package com.payflow.core.merchant.application;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.merchant.domain.Merchant;
import com.payflow.core.merchant.domain.ProviderAccount;
import com.payflow.core.merchant.domain.ProviderAccountStatus;
import com.payflow.core.merchant.persistence.ProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderAccountService implements ProviderAccountResolver {

    private final ProviderAccountRepository providerAccountRepository;
    private final MerchantService merchantService;
    private final SymmetricEncryptor encryptor;

    @Transactional
    public ProviderAccountSummary createProviderAccount(
            UUID organizationId, UUID merchantId, ProviderCode providerCode, String credentialsJson, boolean isDefault) {
        Merchant merchant = merchantService.getEntityWithinOrganization(merchantId, organizationId);

        if (isDefault) {
            providerAccountRepository.findByMerchantIdAndIsDefaultTrue(merchantId)
                    .ifPresent(existingDefault -> {
                        existingDefault.clearDefault();
                        providerAccountRepository.save(existingDefault);
                        // Hibernate flushes inserts before updates by default; without forcing
                        // this update to the DB now, the new row's insert below would race the
                        // clearing update and trip the partial unique index on is_default.
                        providerAccountRepository.flush();
                    });
        }

        byte[] encryptedCredentials = encryptor.encrypt(credentialsJson.getBytes(StandardCharsets.UTF_8));
        ProviderAccount providerAccount = new ProviderAccount(merchant, providerCode, encryptedCredentials, isDefault);
        providerAccountRepository.save(providerAccount);

        return toSummary(providerAccount);
    }

    @Transactional(readOnly = true)
    public List<ProviderAccountSummary> listByMerchant(UUID organizationId, UUID merchantId) {
        merchantService.getEntityWithinOrganization(merchantId, organizationId);
        return providerAccountRepository.findByMerchantId(merchantId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderAccountSummary resolveDefault(UUID merchantId) {
        ProviderAccount account = providerAccountRepository.findByMerchantIdAndIsDefaultTrue(merchantId)
                .filter(this::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active default provider account configured for merchant: " + merchantId));
        return toSummary(account);
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderAccountSummary resolveById(UUID merchantId, UUID providerAccountId) {
        ProviderAccount account = providerAccountRepository.findByIdAndMerchantId(providerAccountId, merchantId)
                .filter(this::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active provider account not found: " + providerAccountId));
        return toSummary(account);
    }

    private boolean isActive(ProviderAccount account) {
        return account.getStatus() == ProviderAccountStatus.ACTIVE;
    }

    private ProviderAccountSummary toSummary(ProviderAccount providerAccount) {
        return new ProviderAccountSummary(
                providerAccount.getId(),
                providerAccount.getMerchant().getId(),
                providerAccount.getProviderCode(),
                providerAccount.isDefault(),
                providerAccount.getStatus(),
                providerAccount.getCreatedAt());
    }
}
