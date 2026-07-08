package com.payflow.core.outbox.application;

import java.util.UUID;

/**
 * The public seam other modules dead-letter through - see EDD section 3's
 * module table, which already declares webhook depends on outbox for
 * exactly this. Callers outside this module must never reach into
 * DeadLetterEventRepository or the DeadLetterEvent entity directly, per the
 * project's module-boundary discipline (application-layer interfaces only).
 */
public interface DeadLetterWriter {

    void write(String sourceType, UUID sourceId, String payload, String errorReason);
}
