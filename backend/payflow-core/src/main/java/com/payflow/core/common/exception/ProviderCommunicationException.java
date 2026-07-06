package com.payflow.core.common.exception;

/**
 * A payment provider could not be reached, or returned a response PayFlow's
 * own state machine doesn't expect (e.g. capturing a charge id it no longer
 * recognizes) - a provider/consistency problem, not a business decline.
 */
public class ProviderCommunicationException extends PayFlowException {

    public ProviderCommunicationException(String message, Throwable cause) {
        super("provider_unavailable", message);
        initCause(cause);
    }

    public ProviderCommunicationException(String message) {
        super("provider_unavailable", message);
    }
}
