package com.payflow.core.provider;

import com.payflow.core.common.provider.ProviderCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves the ProviderClient for a given provider code. Adding a real
 * provider later means implementing ProviderClient and registering it as a
 * bean - this class picks it up automatically, no change needed here. Every
 * registered client is wrapped in ResilientProviderClient (M11) at
 * construction time, so retry-with-backoff and the circuit breaker apply
 * uniformly regardless of provider - callers of resolve() never see the
 * wrapping.
 */
@Component
public class ProviderRegistry {

    private static final String RESILIENCE_INSTANCE_NAME = "providerClient";

    private final Map<ProviderCode, ProviderClient> clientsByCode;

    public ProviderRegistry(List<ProviderClient> clients, RetryRegistry retryRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        Retry retry = retryRegistry.retry(RESILIENCE_INSTANCE_NAME);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE_NAME);
        this.clientsByCode = clients.stream()
                .collect(Collectors.toMap(
                        ProviderClient::providerCode,
                        client -> new ResilientProviderClient(client, retry, circuitBreaker)));
    }

    public ProviderClient resolve(ProviderCode providerCode) {
        ProviderClient client = clientsByCode.get(providerCode);
        if (client == null) {
            throw new IllegalStateException("No provider client registered for " + providerCode);
        }
        return client;
    }
}
