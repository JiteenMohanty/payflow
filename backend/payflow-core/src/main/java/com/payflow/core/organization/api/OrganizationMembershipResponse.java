package com.payflow.core.organization.api;

import com.payflow.core.organization.domain.OrganizationRole;

import java.util.UUID;

public record OrganizationMembershipResponse(UUID organizationId, String organizationName, String organizationSlug, OrganizationRole role) {
}
