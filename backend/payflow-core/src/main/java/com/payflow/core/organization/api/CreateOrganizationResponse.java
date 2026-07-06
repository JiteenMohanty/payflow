package com.payflow.core.organization.api;

import java.util.UUID;

public record CreateOrganizationResponse(UUID organizationId, String organizationSlug, UUID ownerUserId) {
}
