package com.payflow.core.common.exception;

public class ResourceNotFoundException extends PayFlowException {

    public ResourceNotFoundException(String message) {
        super("resource_not_found", message);
    }
}
