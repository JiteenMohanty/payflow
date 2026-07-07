package com.payflow.core.payment.persistence;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * SELECT ... FOR UPDATE variant of findByIdAndOrganizationId, for any
     * caller that loads a payment in order to mutate it. Found via manual
     * verification to be load-bearing, not defensive: a synchronous
     * capture() and a webhook-driven reconcileCaptureConfirmation() for the
     * same payment can run concurrently (the provider's async webhook can
     * arrive before the synchronous capture's own transaction commits), and
     * under READ COMMITTED a plain SELECT in either transaction can see the
     * payment as still AUTHORIZED while the other is mid-flight - both then
     * legitimately (from their own point of view) apply the capture,
     * producing two ledger_transactions and two outbox payment.captured
     * events for one real capture. FOR UPDATE serializes the two: whichever
     * transaction locks the row first, the second blocks until it commits
     * and then correctly observes CAPTURED and no-ops.
     */
    // @Query, not a "lockBy..." derived name - Spring Data JPA only
    // recognizes find/read/get/query/count/exists/delete/remove as query-
    // derivation prefixes; "lockBy..." isn't one, so without @Query it
    // fails at startup trying to parse "lockByIdAndOrganizationId" as a
    // property path instead of treating @Lock as the query modifier it is.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.organizationId = :organizationId")
    Optional<Payment> lockByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Used by webhook reconciliation, which has no tenant context to scope
     * by - (provider_code, provider_reference) is enforced unique at the DB
     * level (V8__inbound_webhook_events.sql) so this can only ever match one
     * row. Locking for the same reason as lockByIdAndOrganizationId above.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerCode = :providerCode AND p.providerReference = :providerReference")
    Optional<Payment> lockByProviderCodeAndProviderReference(ProviderCode providerCode, String providerReference);

    @Query("SELECT p FROM Payment p WHERE p.organizationId = :organizationId "
            + "AND (:merchantId IS NULL OR p.merchantId = :merchantId) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "ORDER BY p.createdAt DESC")
    List<Payment> search(UUID organizationId, UUID merchantId, PaymentStatus status, Pageable pageable);
}
