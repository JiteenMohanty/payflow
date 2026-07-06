package com.payflow.core.organization.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.organization.application.ApiKeyService;
import com.payflow.core.organization.application.ApiKeySummary;
import com.payflow.core.organization.application.CreateApiKeyResult;
import com.payflow.core.organization.application.OrganizationAccessGuard;
import com.payflow.core.organization.domain.OrganizationRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/v1/organizations/{organizationId}/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final OrganizationAccessGuard accessGuard;

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        requireDashboardRole(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        CreateApiKeyResult result = apiKeyService.createApiKey(organizationId, request.environment());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreateApiKeyResponse(result.id(), result.fullKey(), result.keyPrefix(), result.environment()));
    }

    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable UUID organizationId) {
        requireDashboardRole(organizationId, EnumSet.allOf(OrganizationRole.class));

        return apiKeyService.listApiKeys(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID organizationId, @PathVariable UUID apiKeyId) {
        requireDashboardRole(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        apiKeyService.revokeApiKey(organizationId, apiKeyId);
        return ResponseEntity.noContent().build();
    }

    private void requireDashboardRole(UUID organizationId, Set<OrganizationRole> allowedRoles) {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.USER) {
            throw new AccessDeniedException("This endpoint requires a dashboard session");
        }
        accessGuard.requireMembership(organizationId, context.principalId(), allowedRoles);
    }

    private ApiKeyResponse toResponse(ApiKeySummary summary) {
        return new ApiKeyResponse(
                summary.id(), summary.keyPrefix(), summary.environment(), summary.status(),
                summary.lastUsedAt(), summary.createdAt());
    }
}
