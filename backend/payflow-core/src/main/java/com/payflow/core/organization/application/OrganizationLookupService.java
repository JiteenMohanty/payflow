package com.payflow.core.organization.application;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationLookupService {

    OrganizationSummary getById(UUID organizationId);

    Optional<OrganizationSummary> findById(UUID organizationId);
}
