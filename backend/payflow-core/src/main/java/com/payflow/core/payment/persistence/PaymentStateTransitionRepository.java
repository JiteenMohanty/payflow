package com.payflow.core.payment.persistence;

import com.payflow.core.payment.domain.PaymentStateTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentStateTransitionRepository extends JpaRepository<PaymentStateTransition, UUID> {

    List<PaymentStateTransition> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
