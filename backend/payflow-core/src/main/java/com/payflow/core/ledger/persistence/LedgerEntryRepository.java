package com.payflow.core.ledger.persistence;

import com.payflow.core.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Native, with explicit casts on every optionally-null parameter:
     * Postgres cannot infer a bind parameter's type from "? IS NULL" alone
     * when the same parameter is also compared against a joined-table
     * column ({@code ERROR: could not determine data type of parameter}) -
     * a JPQL "(:x IS NULL OR e.assoc.field = :x)" over an association path
     * hits this; a direct root-entity field (as in PaymentRepository) does
     * not, apparently because Hibernate's SQL AST renders that case
     * differently.
     */
    @Query(value = "SELECT le.* FROM ledger_entries le "
            + "JOIN ledger_transactions lt ON lt.id = le.ledger_transaction_id "
            + "WHERE lt.organization_id = :organizationId "
            + "AND (CAST(:paymentId AS uuid) IS NULL OR lt.payment_id = CAST(:paymentId AS uuid)) "
            + "AND (CAST(:createdAfter AS timestamptz) IS NULL OR le.created_at >= CAST(:createdAfter AS timestamptz)) "
            + "AND (CAST(:createdBefore AS timestamptz) IS NULL OR le.created_at <= CAST(:createdBefore AS timestamptz)) "
            + "ORDER BY le.created_at ASC "
            + "LIMIT :limit",
            nativeQuery = true)
    List<LedgerEntry> search(
            UUID organizationId, UUID paymentId, Instant createdAfter, Instant createdBefore, int limit);
}
