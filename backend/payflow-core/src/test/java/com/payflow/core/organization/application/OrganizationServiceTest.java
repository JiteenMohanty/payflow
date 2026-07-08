package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.Organization;
import com.payflow.core.organization.domain.OrganizationMember;
import com.payflow.core.organization.domain.OrganizationRole;
import com.payflow.core.organization.domain.User;
import com.payflow.core.organization.persistence.OrganizationMemberRepository;
import com.payflow.core.organization.persistence.OrganizationRepository;
import com.payflow.core.organization.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private OrganizationService organizationService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationRepository, userRepository, organizationMemberRepository, passwordEncoder);
    }

    @Test
    void listMembershipsMapsEachMembershipToItsOrganizationAndRole() {
        OrganizationMember acmeMembership = membership("Acme", "acme", OrganizationRole.OWNER);
        OrganizationMember globexMembership = membership("Globex", "globex", OrganizationRole.ANALYST);
        when(organizationMemberRepository.findByUserIdOrderByOrganizationNameAsc(userId))
                .thenReturn(List.of(acmeMembership, globexMembership));

        List<OrganizationMembershipSummary> memberships = organizationService.listMemberships(userId);

        assertThat(memberships).hasSize(2);
        assertThat(memberships.get(0).organizationName()).isEqualTo("Acme");
        assertThat(memberships.get(0).organizationSlug()).isEqualTo("acme");
        assertThat(memberships.get(0).role()).isEqualTo(OrganizationRole.OWNER);
        assertThat(memberships.get(1).organizationName()).isEqualTo("Globex");
        assertThat(memberships.get(1).role()).isEqualTo(OrganizationRole.ANALYST);
    }

    @Test
    void listMembershipsReturnsEmptyWhenTheUserBelongsToNoOrganization() {
        when(organizationMemberRepository.findByUserIdOrderByOrganizationNameAsc(userId)).thenReturn(List.of());

        assertThat(organizationService.listMemberships(userId)).isEmpty();
    }

    private OrganizationMember membership(String orgName, String slug, OrganizationRole role) {
        Organization organization = new Organization(orgName, slug);
        User user = new User("user-" + UUID.randomUUID() + "@example.com", "hash", "Some User");
        return new OrganizationMember(organization, user, role);
    }
}
