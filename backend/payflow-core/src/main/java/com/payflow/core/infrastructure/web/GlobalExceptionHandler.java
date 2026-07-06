package com.payflow.core.infrastructure.web;

import com.payflow.core.common.exception.ConflictException;
import com.payflow.core.common.exception.ErrorResponse;
import com.payflow.core.common.exception.PayFlowException;
import com.payflow.core.common.exception.ProviderCommunicationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Lowest precedence so module-specific advices (e.g. PaymentExceptionHandler)
 * always get first refusal on their own exception types - Spring picks the
 * first advice bean with any applicable handler, not the most specific
 * handler across all beans, so the generic PayFlowException catch-all here
 * must never outrank a more specific one declared elsewhere.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "invalid_request_error", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return respond(HttpStatus.CONFLICT, "invalid_request_error", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ProviderCommunicationException.class)
    public ResponseEntity<ErrorResponse> handleProviderCommunication(ProviderCommunicationException ex) {
        log.error("Provider communication failure", ex);
        return respond(HttpStatus.BAD_GATEWAY, "provider_error", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PayFlowException.class)
    public ResponseEntity<ErrorResponse> handleDomain(PayFlowException ex) {
        return respond(HttpStatus.BAD_REQUEST, "invalid_request_error", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "authorization_error", "forbidden", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "authentication_error", "unauthorized", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "invalid_request_error", "validation_error", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "api_error", "internal_error", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String type, String code, String message) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(ErrorResponse.of(type, code, message, traceId));
    }
}
