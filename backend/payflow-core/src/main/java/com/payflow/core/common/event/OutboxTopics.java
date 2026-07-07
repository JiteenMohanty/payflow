package com.payflow.core.common.event;

/**
 * Kafka topic names from the event catalog - see EDD section 6. Centralized
 * here so producers (payment, refund) don't each hardcode the same string
 * literal.
 */
public final class OutboxTopics {

    public static final String PAYMENTS = "payflow.payments";
    public static final String REFUNDS = "payflow.refunds";
    public static final String LEDGER = "payflow.ledger";

    private OutboxTopics() {
    }
}
