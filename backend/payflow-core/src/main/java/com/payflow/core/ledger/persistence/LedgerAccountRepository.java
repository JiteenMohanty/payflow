package com.payflow.core.ledger.persistence;

import com.payflow.core.ledger.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByOrganizationIdAndCode(UUID organizationId, String code);
}
