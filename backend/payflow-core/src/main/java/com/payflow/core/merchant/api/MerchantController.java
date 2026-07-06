package com.payflow.core.merchant.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.merchant.application.MerchantService;
import com.payflow.core.merchant.application.MerchantSummary;
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
@RequestMapping("/v1/organizations/{organizationId}/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final OrganizationAccessGuard accessGuard;

    @PostMapping
    public ResponseEntity<MerchantResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateMerchantRequest request) {
        requireDashboardRole(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        MerchantSummary summary = merchantService.createMerchant(organizationId, request.name(), request.defaultCurrency());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    @GetMapping
    public List<MerchantResponse> list(@PathVariable UUID organizationId) {
        requireDashboardRole(organizationId, EnumSet.allOf(OrganizationRole.class));

        return merchantService.listByOrganization(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{merchantId}")
    public MerchantResponse get(@PathVariable UUID organizationId, @PathVariable UUID merchantId) {
        requireDashboardRole(organizationId, EnumSet.allOf(OrganizationRole.class));

        return toResponse(merchantService.getById(merchantId));
    }

    private void requireDashboardRole(UUID organizationId, Set<OrganizationRole> allowedRoles) {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.USER) {
            throw new AccessDeniedException("This endpoint requires a dashboard session");
        }
        accessGuard.requireMembership(organizationId, context.principalId(), allowedRoles);
    }

    private MerchantResponse toResponse(MerchantSummary summary) {
        return new MerchantResponse(
                summary.id(), summary.organizationId(), summary.name(), summary.defaultCurrency(), summary.status());
    }
}
