package com.payflow.core.ledger.api;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.ledger.application.LedgerEntrySummary;
import com.payflow.core.ledger.application.LedgerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Merchant-facing, API-key authenticated only - same convention as
 * PaymentController.
 */
@RestController
@RequestMapping("/v1/ledger/entries")
@RequiredArgsConstructor
public class LedgerEntryController {

    private final LedgerQueryService ledgerQueryService;

    @GetMapping
    public List<LedgerEntryResponse> list(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) Integer limit) {
        UUID organizationId = requireApiKeyOrganization();
        return ledgerQueryService.listEntries(organizationId, paymentId, createdAfter, createdBefore, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private UUID requireApiKeyOrganization() {
        TenantContext context = TenantContextHolder.current();
        if (context.principalType() != PrincipalType.API_KEY) {
            throw new AccessDeniedException("This endpoint requires an API key");
        }
        return context.organizationId();
    }

    private LedgerEntryResponse toResponse(LedgerEntrySummary summary) {
        return new LedgerEntryResponse(
                summary.id(), summary.ledgerTransactionId(), summary.paymentId(), summary.accountCode(),
                summary.entryType(), summary.amount(), summary.currency(), summary.createdAt());
    }
}
