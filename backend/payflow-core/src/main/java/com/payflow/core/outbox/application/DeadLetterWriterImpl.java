package com.payflow.core.outbox.application;

import com.payflow.core.outbox.domain.DeadLetterEvent;
import com.payflow.core.outbox.persistence.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class DeadLetterWriterImpl implements DeadLetterWriter {

    private final DeadLetterEventRepository deadLetterEventRepository;

    @Override
    public void write(String sourceType, UUID sourceId, String payload, String errorReason) {
        deadLetterEventRepository.save(new DeadLetterEvent(sourceType, sourceId, payload, errorReason));
    }
}
