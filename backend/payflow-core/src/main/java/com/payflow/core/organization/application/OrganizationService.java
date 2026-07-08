package com.payflow.core.organization.application;

import com.payflow.core.common.exception.ConflictException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.organization.domain.Organization;
import com.payflow.core.organization.domain.OrganizationMember;
import com.payflow.core.organization.domain.OrganizationRole;
import com.payflow.core.organization.domain.User;
import com.payflow.core.organization.persistence.OrganizationMemberRepository;
import com.payflow.core.organization.persistence.OrganizationRepository;
import com.payflow.core.organization.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService implements OrganizationLookupService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public OrganizationSignupResult signUp(String organizationName, String ownerEmail, String ownerFullName, String rawPassword) {
        if (userRepository.existsByEmail(ownerEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        Organization organization = new Organization(organizationName, generateUniqueSlug(organizationName));
        organizationRepository.save(organization);

        User owner = new User(ownerEmail, passwordEncoder.encode(rawPassword), ownerFullName);
        userRepository.save(owner);

        OrganizationMember membership = new OrganizationMember(organization, owner, OrganizationRole.OWNER);
        organizationMemberRepository.save(membership);

        return new OrganizationSignupResult(organization.getId(), organization.getSlug(), owner.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationSummary getById(UUID organizationId) {
        return findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationSummary> findById(UUID organizationId) {
        return organizationRepository.findById(organizationId).map(this::toSummary);
    }

    /**
     * Backs the dashboard's org picker: a JWT session's TenantContext has no
     * organizationId (see TenantContext's own class-level note - a user can
     * belong to more than one org), so the frontend needs this right after
     * login to know which organization(s) to route into.
     */
    @Transactional(readOnly = true)
    public List<OrganizationMembershipSummary> listMemberships(UUID userId) {
        return organizationMemberRepository.findByUserIdOrderByOrganizationNameAsc(userId).stream()
                .map(member -> new OrganizationMembershipSummary(
                        member.getOrganization().getId(), member.getOrganization().getName(),
                        member.getOrganization().getSlug(), member.getRole()))
                .toList();
    }

    private OrganizationSummary toSummary(Organization organization) {
        return new OrganizationSummary(organization.getId(), organization.getName(), organization.getSlug(), organization.getStatus());
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "org";
        }
        String candidate = base;
        int suffix = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }
}
