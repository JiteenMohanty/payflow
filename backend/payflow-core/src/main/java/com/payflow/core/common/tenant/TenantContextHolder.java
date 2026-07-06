package com.payflow.core.common.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the tenant context stashed on the current request's Spring Security
 * {@code Authentication} (via {@code getDetails()}) by the security module's
 * authentication filters. This piggybacks on {@code SecurityContextHolderFilter}
 * for per-request lifecycle management rather than maintaining a second,
 * hand-rolled ThreadLocal - one less thing that can leak across pooled
 * request threads. Depending on spring-security-core (a plain library, not
 * the {@code security} module) does not violate module boundaries.
 */
public final class TenantContextHolder {

    private TenantContextHolder() {
    }

    public static TenantContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof TenantContext tenantContext) {
            return tenantContext;
        }
        throw new IllegalStateException("No tenant context bound to the current request");
    }
}
