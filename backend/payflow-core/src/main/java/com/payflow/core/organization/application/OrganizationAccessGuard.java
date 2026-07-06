package com.payflow.core.organization.application;

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
}
