package com.payflow.core.payment.api;

import com.payflow.core.common.exception.ErrorResponse;
import com.payflow.core.infrastructure.web.CorrelationIdFilter;
import com.payflow.core.payment.domain.IllegalPaymentTransitionException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(IllegalPaymentTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalPaymentTransitionException ex) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("invalid_request_error", ex.getCode(), ex.getMessage(), traceId));
    }
}
