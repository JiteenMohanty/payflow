package com.payflow.core.common.exception;

public class ConflictException extends PayFlowException {

    public ConflictException(String message) {
        super("conflict", message);
    }
}
