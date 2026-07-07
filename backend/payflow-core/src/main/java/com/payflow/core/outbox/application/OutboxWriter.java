package com.payflow.core.outbox.application;

import java.util.UUID;

/**
 * The only way any module writes an outbox_events row - see EDD section 3
 * ("OutboxWriter (used inside the same DB transaction)"). Callers invoke
 * this from within their own @Transactional method, never separately, so
 * the outbox row commits atomically with the business row it describes
 * (ADR-0005).
 */
public interface OutboxWriter {

    void write(String aggregateType, UUID aggregateId, String eventType, String kafkaTopic, Object payload);
}
