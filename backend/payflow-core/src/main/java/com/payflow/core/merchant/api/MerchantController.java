package com.payflow.core.merchant.api;

import com.payflow.core.merchant.application.MerchantService;
import com.payflow.core.merchant.application.MerchantSummary;
import com.payflow.core.organization.application.OrganizationAccessGuard;
import com.payflow.core.organization.domain.OrganizationRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
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
        accessGuard.requireDashboardMembership(organizationId, EnumSet.of(OrganizationRole.OWNER, OrganizationRole.ADMIN));

        MerchantSummary summary = merchantService.createMerchant(organizationId, request.name(), request.defaultCurrency());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    @GetMapping
    public List<MerchantResponse> list(@PathVariable UUID organizationId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return merchantService.listByOrganization(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{merchantId}")
    public MerchantResponse get(@PathVariable UUID organizationId, @PathVariable UUID merchantId) {
        accessGuard.requireDashboardMembership(organizationId, EnumSet.allOf(OrganizationRole.class));

        return toResponse(merchantService.getById(merchantId));
    }

    private MerchantResponse toResponse(MerchantSummary summary) {
        return new MerchantResponse(
                summary.id(), summary.organizationId(), summary.name(), summary.defaultCurrency(), summary.status());
    }
}
