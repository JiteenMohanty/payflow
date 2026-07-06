package com.payflow.core.common.exception;

public class DomainValidationException extends PayFlowException {

    public DomainValidationException(String message) {
        super("validation_error", message);
    }
}
