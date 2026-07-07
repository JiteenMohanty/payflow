package com.payflow.core.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxWriterImpl implements OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void write(String aggregateType, UUID aggregateId, String eventType, String kafkaTopic, Object payload) {
        try {
            String serializedPayload = objectMapper.writeValueAsString(payload);
            repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, kafkaTopic, serializedPayload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }
}
