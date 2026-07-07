package com.payflow.core.security.hmac;

/**
 * Generic HMAC-SHA256 sign/verify primitive - security owns "HMAC sign/verify"
 * per EDD section 3. Callers own their own signed-string convention (e.g.
 * the webhook module's "{timestamp}.{rawBody}" scheme) - this class knows
 * nothing about webhooks specifically, so the same primitive serves both
 * inbound provider webhook verification (M7) and outbound merchant webhook
 * signing (M8).
 */
public interface HmacSigner {

    String sign(String secret, String payload);

    boolean verify(String secret, String payload, String expectedHexDigest);
}
