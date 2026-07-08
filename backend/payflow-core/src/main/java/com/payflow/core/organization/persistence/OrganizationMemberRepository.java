package com.payflow.core.organization.persistence;

import com.payflow.core.organization.domain.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /**
     * Backs "list my organizations" (dashboard org-picker after login, since
     * a JWT session's TenantContext.organizationId() is null - a user can
     * belong to more than one org). Ordering by organization name gives the
     * frontend a stable, human-sensible default without it needing to sort.
     */
    List<OrganizationMember> findByUserIdOrderByOrganizationNameAsc(UUID userId);
}
