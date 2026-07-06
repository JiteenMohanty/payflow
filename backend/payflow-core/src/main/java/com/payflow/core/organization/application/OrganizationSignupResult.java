package com.payflow.core.organization.application;

import java.util.UUID;

public record OrganizationSignupResult(UUID organizationId, String organizationSlug, UUID ownerUserId) {
}
