package com.payflow.core.provider;

import com.payflow.core.common.provider.ProviderCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the ProviderClient for a given provider code. Adding a real
 * provider later means implementing ProviderClient and registering it as a
 * bean - this class picks it up automatically, no change needed here.
 */
@Component
public class ProviderRegistry {

    private final Map<ProviderCode, ProviderClient> clientsByCode;

    public ProviderRegistry(List<ProviderClient> clients) {
        this.clientsByCode = clients.stream()
                .collect(Collectors.toMap(ProviderClient::providerCode, Function.identity()));
    }

    public ProviderClient resolve(ProviderCode providerCode) {
        ProviderClient client = clientsByCode.get(providerCode);
        if (client == null) {
            throw new IllegalStateException("No provider client registered for " + providerCode);
        }
        return client;
    }
}
