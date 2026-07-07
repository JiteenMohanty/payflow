package com.payflow.core.webhook.inbound.api;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.webhook.inbound.application.InboundWebhookProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by API key or JWT (providers have neither) - authenticated
 * instead by HMAC signature, verified inside InboundWebhookProcessor against
 * the raw request body. See EDD section 5.4 and SecurityConfig's permitAll
 * entry for this path.
 */
@RestController
@RequiredArgsConstructor
public class InboundWebhookController {

    private final InboundWebhookProcessor processor;

    @PostMapping("/v1/webhooks/providers/{providerCode}")
    public ResponseEntity<Void> receive(
            @PathVariable String providerCode,
            @RequestHeader("X-Mock-Signature") String signature,
            @RequestBody String rawBody) {
        // The EDD's own documented endpoint is lowercase
        // (/v1/webhooks/providers/mock), a normal REST URL convention, while
        // ProviderCode's enum literals are uppercase (normal Java
        // convention) - Spring's default enum path-variable converter is
        // case-sensitive, so binding directly to ProviderCode here 500s on
        // exactly the URL the EDD documents. Converting explicitly avoids
        // forcing either convention to bend to the other.
        ProviderCode resolvedProviderCode;
        try {
            resolvedProviderCode = ProviderCode.valueOf(providerCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        processor.process(resolvedProviderCode, rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
