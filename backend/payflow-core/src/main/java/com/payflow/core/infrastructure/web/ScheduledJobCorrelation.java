package com.payflow.core.infrastructure.web;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Gives a scheduled job (no request-scoped MDC context of its own) a fresh
 * correlation id for the duration of one run, so its log lines - and any
 * outbound HTTP call it makes through CorrelationIdClientInterceptor -
 * correlate as one unit of work (ADR-0012).
 */
public final class ScheduledJobCorrelation {

    private ScheduledJobCorrelation() {
    }

    public static void runWithFreshCorrelationId(Runnable job) {
        MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        try {
            job.run();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
