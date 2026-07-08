package com.payflow.core.organization.application;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.organization.domain.OrganizationMember;
import com.payflow.core.organization.domain.OrganizationRole;
import com.payflow.core.organization.persistence.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationAccessGuard {

    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationRole requireMembership(UUID organizationId, UUID userId, Set<OrganizationRole> allowedRoles) {
        OrganizationMember membership = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this organization"));
        if (!allowedRoles.contains(membership.getRole())) {
            throw new AccessDeniedException("Role " + membership.getRole() + " is not permitted to perform this action");
        }
        return membership.getRole();
    }

    /**
     * The "JWT dashboard session + org membership + role" check every
     * dashboard-facing controller needs (MerchantController's own version
     * of this, OrganizationController's, and now every M12 dashboard
     * endpoint) - centralized here rather than duplicated per controller,
     * since it reads TenantContextHolder itself instead of asking each
     * caller to pass principalId through.
     */
    public OrganizationRole requireDashboardMembership(UUID organizationId, Set<OrganizationRole> allowedRoles) {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.USER) {
            throw new AccessDeniedException("This endpoint requires a dashboard session");
        }
        return requireMembership(organizationId, context.principalId(), allowedRoles);
    }
}
