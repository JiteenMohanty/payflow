package com.payflow.core.provider;

import com.payflow.core.common.exception.ProviderCommunicationException;
import com.payflow.core.common.provider.ProviderCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.function.Supplier;

/**
 * Wraps any ProviderClient with retry-with-backoff and a circuit breaker
 * (EDD risk table §12: "Exponential backoff with jitter on ... provider
 * client retries; circuit breaker around ProviderClient"), so every adapter
 * - Mock today, a real provider later - gets the same resilience without
 * reimplementing it, matching ADR-0006's "payment/refund depend only on
 * ProviderClient" intent. Not a Spring bean: ProviderRegistry constructs one
 * per registered client, since there's no single delegate to inject.
 *
 * CircuitBreaker wraps Retry, not the reverse - Resilience4j's own
 * documented composition order, so an open circuit fails fast without even
 * attempting a retry cycle. CallNotPermittedException (thrown when the
 * circuit is open) is caught and rewrapped as ProviderCommunicationException
 * so the resilience mechanics stay entirely invisible at the ProviderClient
 * interface boundary - every existing caller (PaymentService, RefundService)
 * already only expects that one exception type from a provider call.
 */
public class ResilientProviderClient implements ProviderClient {

    private final ProviderClient delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientProviderClient(ProviderClient delegate, Retry retry, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ProviderCode providerCode() {
        return delegate.providerCode();
    }

    @Override
    public ProviderAuthorizationResult authorize(ProviderAuthorizationRequest request) {
        return decorate(() -> delegate.authorize(request));
    }

    @Override
    public ProviderCaptureResult capture(ProviderCaptureRequest request) {
        return decorate(() -> delegate.capture(request));
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest request) {
        return decorate(() -> delegate.refund(request));
    }

    @Override
    public ProviderChargeStatusResult checkStatus(String providerReference) {
        return decorate(() -> delegate.checkStatus(providerReference));
    }

    private <T> T decorate(Supplier<T> call) {
        Supplier<T> withRetry = Retry.decorateSupplier(retry, call);
        Supplier<T> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);
        try {
            return withCircuitBreaker.get();
        } catch (CallNotPermittedException e) {
            throw new ProviderCommunicationException("Provider circuit breaker is open for " + delegate.providerCode(), e);
        }
    }
}
