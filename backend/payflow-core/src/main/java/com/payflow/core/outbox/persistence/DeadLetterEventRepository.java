package com.payflow.core.outbox.persistence;

import com.payflow.core.outbox.domain.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
}
