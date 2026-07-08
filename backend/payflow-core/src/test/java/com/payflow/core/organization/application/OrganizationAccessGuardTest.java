package com.payflow.core.organization.application;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.organization.domain.Organization;
import com.payflow.core.organization.domain.OrganizationMember;
import com.payflow.core.organization.domain.OrganizationRole;
import com.payflow.core.organization.domain.User;
import com.payflow.core.organization.persistence.OrganizationMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessGuardTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    private OrganizationAccessGuard accessGuard;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accessGuard = new OrganizationAccessGuard(organizationMemberRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireMembershipReturnsTheRoleWhenTheUserIsAMemberWithAnAllowedRole() {
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId))
                .thenReturn(Optional.of(memberWithRole(OrganizationRole.ADMIN)));

        OrganizationRole role = accessGuard.requireMembership(organizationId, userId, EnumSet.allOf(OrganizationRole.class));

        assertThat(role).isEqualTo(OrganizationRole.ADMIN);
    }

    @Test
    void requireMembershipRejectsANonMember() {
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessGuard.requireMembership(organizationId, userId, EnumSet.allOf(OrganizationRole.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireMembershipRejectsARoleNotInTheAllowedSet() {
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId))
                .thenReturn(Optional.of(memberWithRole(OrganizationRole.ANALYST)));

        assertThatThrownBy(() -> accessGuard.requireMembership(
                organizationId, userId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDashboardMembershipRejectsAnApiKeyPrincipal() {
        bindTenantContext(new TenantContext(organizationId, PrincipalType.API_KEY, UUID.randomUUID(), "test", Set.of()));

        assertThatThrownBy(() -> accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("dashboard session");
    }

    @Test
    void requireDashboardMembershipDelegatesToRequireMembershipForAUserPrincipal() {
        bindTenantContext(new TenantContext(null, PrincipalType.USER, userId, null, Set.of()));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId))
                .thenReturn(Optional.of(memberWithRole(OrganizationRole.OWNER)));

        OrganizationRole role = accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        assertThat(role).isEqualTo(OrganizationRole.OWNER);
    }

    @Test
    void requireDashboardMembershipRejectsAUserPrincipalWithADisallowedRole() {
        bindTenantContext(new TenantContext(null, PrincipalType.USER, userId, null, Set.of()));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId))
                .thenReturn(Optional.of(memberWithRole(OrganizationRole.ANALYST)));

        assertThatThrownBy(() -> accessGuard.requireDashboardMembership(
                organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private OrganizationMember memberWithRole(OrganizationRole role) {
        Organization organization = new Organization("Acme", "acme-" + UUID.randomUUID());
        User user = new User("owner@example.com", "hash", "Ada Owner");
        return new OrganizationMember(organization, user, role);
    }

    private void bindTenantContext(TenantContext tenantContext) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(tenantContext.principalId(), null);
        authentication.setDetails(tenantContext);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
