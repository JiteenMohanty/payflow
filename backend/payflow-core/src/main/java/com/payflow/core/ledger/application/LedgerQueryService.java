package com.payflow.core.ledger.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerQueryService {

    List<LedgerEntrySummary> listEntries(
            UUID organizationId, UUID paymentId, Instant createdAfter, Instant createdBefore, Integer limit);
}
