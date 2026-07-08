package com.payflow.core.infrastructure.web;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Adds the current request's correlation id (from MDC, set by
 * CorrelationIdFilter) as an outbound header on every provider/webhook HTTP
 * call, so a merchant-facing request stays traceable across the process
 * boundary (ADR-0012). A no-op when nothing is on MDC for the calling
 * thread - a caller running on a background thread (a scheduled job, an
 * async callback) must put its own correlation id on MDC first, since MDC
 * is thread-local and doesn't cross a thread handoff on its own.
 */
@Component
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        return execution.execute(request, body);
    }
}
