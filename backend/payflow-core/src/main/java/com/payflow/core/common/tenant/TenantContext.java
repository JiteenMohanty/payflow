package com.payflow.core.common.tenant;

import java.util.Set;
import java.util.UUID;

public record TenantContext(
        UUID organizationId,
        PrincipalType principalType,
        UUID principalId,
        String environment,
        Set<String> roles
) {
}
