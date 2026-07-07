package com.payflow.core.refund.persistence;

import com.payflow.core.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
