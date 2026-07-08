package com.payflow.core.infrastructure.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdClientInterceptorTest {

    @Mock
    private ClientHttpRequestExecution execution;

    private final CorrelationIdClientInterceptor interceptor = new CorrelationIdClientInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void setsTheHeaderFromMdcWhenPresent() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(CorrelationIdFilter.HEADER_NAME)).isEqualTo("corr-123");
        verify(execution).execute(request, new byte[0]);
    }

    @Test
    void leavesTheHeaderUnsetWhenNothingIsOnMdc() throws Exception {
        // request.getHeaders() is deliberately not stubbed here: the
        // interceptor's null-correlationId branch never calls it at all
        // (see intercept()'s early-exit if), so asserting that is exactly
        // what proves this branch does nothing to the request.
        HttpRequest request = mock(HttpRequest.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        interceptor.intercept(request, new byte[0], execution);

        verify(request, never()).getHeaders();
    }
}
