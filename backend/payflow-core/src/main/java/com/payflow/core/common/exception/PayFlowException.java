package com.payflow.core.common.exception;

public abstract class PayFlowException extends RuntimeException {

    private final String code;

    protected PayFlowException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
