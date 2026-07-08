package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.OrganizationRole;

import java.util.UUID;

public record OrganizationMembershipSummary(
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        OrganizationRole role
) {
}
