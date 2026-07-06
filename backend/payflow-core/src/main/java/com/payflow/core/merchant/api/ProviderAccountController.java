package com.payflow.core.merchant.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.merchant.application.ProviderAccountService;
import com.payflow.core.merchant.application.ProviderAccountSummary;
import com.payflow.core.organization.application.OrganizationAccessGuard;
import com.payflow.core.organization.domain.OrganizationRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/organizations/{organizationId}/merchants/{merchantId}/provider-accounts")
@RequiredArgsConstructor
public class ProviderAccountController {

    private final ProviderAccountService providerAccountService;
    private final OrganizationAccessGuard accessGuard;

    @PostMapping
    public ResponseEntity<ProviderAccountResponse> create(
            @PathVariable UUID organizationId,
            @PathVariable UUID merchantId,
            @Valid @RequestBody CreateProviderAccountRequest request) {
        requireDashboardRole(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        ProviderAccountSummary summary = providerAccountService.createProviderAccount(
                organizationId, merchantId, request.providerCode(), request.credentialsJson(), request.isDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    @GetMapping
    public List<ProviderAccountResponse> list(@PathVariable UUID organizationId, @PathVariable UUID merchantId) {
        requireDashboardRole(organizationId, EnumSet.allOf(OrganizationRole.class));

        return providerAccountService.listByMerchant(organizationId, merchantId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireDashboardRole(UUID organizationId, Set<OrganizationRole> allowedRoles) {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.USER) {
            throw new AccessDeniedException("This endpoint requires a dashboard session");
        }
        accessGuard.requireMembership(organizationId, context.principalId(), allowedRoles);
    }

    private ProviderAccountResponse toResponse(ProviderAccountSummary summary) {
        return new ProviderAccountResponse(
                summary.id(), summary.merchantId(), summary.providerCode(), summary.isDefault(),
                summary.status(), summary.createdAt());
    }
}
