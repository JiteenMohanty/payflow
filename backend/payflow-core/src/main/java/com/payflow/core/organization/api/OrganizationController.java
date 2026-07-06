package com.payflow.core.organization.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.organization.application.OrganizationAccessGuard;
import com.payflow.core.organization.application.OrganizationService;
import com.payflow.core.organization.application.OrganizationSignupResult;
import com.payflow.core.organization.application.OrganizationSummary;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationAccessGuard accessGuard;

    @PostMapping
    public ResponseEntity<CreateOrganizationResponse> signUp(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationSignupResult result = organizationService.signUp(
                request.organizationName(), request.ownerEmail(), request.ownerFullName(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateOrganizationResponse(
                result.organizationId(), result.organizationSlug(), result.ownerUserId()));
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(@PathVariable UUID organizationId) {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.USER) {
            throw new AccessDeniedException("This endpoint requires a dashboard session");
        }
        accessGuard.requireMembership(organizationId, context.principalId(), EnumSet.allOf(OrganizationRole.class));

        OrganizationSummary summary = organizationService.getById(organizationId);
        return new OrganizationResponse(summary.id(), summary.name(), summary.slug(), summary.status());
    }
}
