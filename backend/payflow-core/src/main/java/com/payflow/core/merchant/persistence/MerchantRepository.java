package com.payflow.core.merchant.persistence;

import com.payflow.core.merchant.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    List<Merchant> findByOrganizationId(UUID organizationId);

    Optional<Merchant> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
