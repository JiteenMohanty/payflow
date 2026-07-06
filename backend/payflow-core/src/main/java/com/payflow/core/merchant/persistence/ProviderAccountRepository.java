package com.payflow.core.merchant.persistence;

import com.payflow.core.merchant.domain.ProviderAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderAccountRepository extends JpaRepository<ProviderAccount, UUID> {

    List<ProviderAccount> findByMerchantId(UUID merchantId);

    Optional<ProviderAccount> findByMerchantIdAndIsDefaultTrue(UUID merchantId);
}
