package com.payflow.core.idempotency.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.exception.ErrorResponse;
import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.common.tenant.TenantContextHolder;
import com.payflow.core.idempotency.application.IdempotencyCheckResult;
import com.payflow.core.idempotency.application.IdempotencyGuard;
import com.payflow.core.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Wraps every API-key authenticated mutating request carrying an
 * Idempotency-Key header - see ADR-0007 and EDD section 7.6. Scoped to API
 * keys only: JWT-authenticated dashboard sessions have no organizationId on
 * their TenantContext (a user can belong to multiple orgs), and dashboard
 * mutations are a human-clicking-a-button scenario, not the network-retry
 * scenario this exists for.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER_NAME);
        if (key == null || key.isBlank() || !MUTATING_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        TenantContext context;
        try {
            context = TenantContextHolder.current();
        } catch (IllegalStateException notAuthenticated) {
            filterChain.doFilter(request, response);
            return;
        }
        if (context.principalType() != PrincipalType.API_KEY) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID organizationId = context.organizationId();
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String fingerprint = computeFingerprint(request.getMethod(), request.getRequestURI(), wrappedRequest.getCachedBody());

        IdempotencyCheckResult result = idempotencyGuard.check(organizationId, key, fingerprint);

        if (result instanceof IdempotencyCheckResult.Replay replay) {
            writeReplay(response, replay.statusCode(), replay.responseBody());
            return;
        }
        if (result instanceof IdempotencyCheckResult.InProgress) {
            writeError(response, HttpStatus.CONFLICT.value(), "invalid_request_error", "idempotency_key_in_progress",
                    "A request with this idempotency key is already in progress");
            return;
        }
        if (result instanceof IdempotencyCheckResult.FingerprintMismatch) {
            writeError(response, HttpStatus.UNPROCESSABLE_ENTITY.value(), "invalid_request_error", "idempotency_key_reused",
                    "This idempotency key was already used with a different request");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        boolean cacheableOutcome = false;
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            cacheableOutcome = wrappedResponse.getStatus() < 500;
        } finally {
            if (cacheableOutcome) {
                String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                idempotencyGuard.complete(organizationId, key, fingerprint, wrappedResponse.getStatus(), body);
            } else {
                // Let a retry with the same key start fresh rather than being
                // permanently stuck replaying a transient server error.
                idempotencyGuard.abandon(organizationId, key);
            }
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void writeReplay(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }

    private void writeError(HttpServletResponse response, int status, String type, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(type, code, message, traceId)));
    }

    private String computeFingerprint(String method, String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
