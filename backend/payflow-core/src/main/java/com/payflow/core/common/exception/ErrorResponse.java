package com.payflow.core.common.exception;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String type, String code, String message, String traceId) {
    }

    public static ErrorResponse of(String type, String code, String message, String traceId) {
        return new ErrorResponse(new ErrorBody(type, code, message, traceId));
    }
}
