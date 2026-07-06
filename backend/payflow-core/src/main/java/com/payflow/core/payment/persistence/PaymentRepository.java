package com.payflow.core.payment.persistence;

import com.payflow.core.payment.domain.Payment;
import com.payflow.core.payment.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("SELECT p FROM Payment p WHERE p.organizationId = :organizationId "
            + "AND (:merchantId IS NULL OR p.merchantId = :merchantId) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "ORDER BY p.createdAt DESC")
    List<Payment> search(UUID organizationId, UUID merchantId, PaymentStatus status, Pageable pageable);
}
