package com.payflow.core.webhook.inbound.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.payment.application.PaymentReconciliationSupport;
import com.payflow.core.security.hmac.HmacSigner;
import com.payflow.core.webhook.inbound.domain.InboundWebhookEvent;
import com.payflow.core.webhook.inbound.domain.InboundWebhookProcessingStatus;
import com.payflow.core.webhook.inbound.persistence.InboundWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Only MOCK is a supported provider for M7 - a real second provider would
 * need its own signature scheme and secret-resolution strategy (each
 * provider signs differently), which is exactly the kind of provider-
 * specific detail ProviderClient/ProviderRegistry already exists to
 * isolate (ADR-0006); a single @Value secret is the honest reflection of
 * "only one provider exists today," not a design that pretends to be
 * multi-provider-ready before it needs to be.
 */
@Service
@RequiredArgsConstructor
public class InboundWebhookService implements InboundWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundWebhookService.class);

    // charge.authorized and charge.refunded are received, signature-verified,
    // deduplicated, and recorded, but never reconciled - see PaymentService's
    // class-level note for why only capture confirmation is in scope for M7.
    private static final Set<String> RECONCILING_EVENT_TYPES = Set.of("charge.captured");
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final InboundWebhookEventRepository repository;
    private final PaymentReconciliationSupport paymentReconciliationSupport;
    private final HmacSigner hmacSigner;
    private final ObjectMapper objectMapper;

    @Value("${payflow.provider.mock.webhook-secret}")
    private String mockProviderWebhookSecret;

    @Override
    @Transactional
    public void process(ProviderCode providerCode, String rawBody, String signatureHeader) {
        if (providerCode != ProviderCode.MOCK) {
            throw new BadCredentialsException("Unsupported provider for inbound webhooks: " + providerCode);
        }
        verifySignature(rawBody, signatureHeader);

        ProviderWebhookPayload payload = parse(rawBody);

        // Deduplicated on (provider_code, provider_event_id) - a retried
        // delivery of an event we've already recorded is a no-op, not an
        // error (ADR-0011). Checked explicitly rather than relying on the
        // unique constraint + catching DataIntegrityViolationException:
        // Postgres aborts the entire transaction on any statement failure,
        // so catching the Java exception doesn't recover it - the
        // subsequent commit throws UnexpectedRollbackException instead,
        // meaning every legitimate retry would 500. A residual true race
        // (two deliveries of the same event landing simultaneously) still
        // hits the unique constraint and 500s - acceptable, since webhook
        // delivery is expected to be retried on error (ADR-0002).
        if (repository.existsByProviderCodeAndProviderEventId(providerCode, payload.eventId())) {
            return;
        }

        InboundWebhookEvent event = new InboundWebhookEvent(
                providerCode, payload.eventId(), payload.eventType(), rawBody, true, InboundWebhookProcessingStatus.PROCESSED);
        repository.save(event);

        if (RECONCILING_EVENT_TYPES.contains(payload.eventType())) {
            paymentReconciliationSupport.reconcileCaptureConfirmation(
                    providerCode, payload.chargeId(), payload.amount(), payload.currency());
        }
    }

    // Invalid-signature attempts are rejected here, before any persistence -
    // a forged or malformed request never reaches inbound_webhook_events, so
    // an attacker can never plant a provider_event_id that would collide
    // with (and shadow) a legitimate future event carrying the same id.
    private void verifySignature(String rawBody, String signatureHeader) {
        String timestampPart = null;
        String digestPart = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                timestampPart = kv[1];
            } else if ("v1".equals(kv[0])) {
                digestPart = kv[1];
            }
        }
        if (timestampPart == null || digestPart == null) {
            throw new BadCredentialsException("Malformed webhook signature header");
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampPart);
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Malformed webhook signature timestamp");
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestampSeconds) > SIGNATURE_TOLERANCE_SECONDS) {
            throw new BadCredentialsException("Webhook signature timestamp outside tolerance window");
        }

        String signedPayload = timestampPart + "." + rawBody;
        if (!hmacSigner.verify(mockProviderWebhookSecret, signedPayload, digestPart)) {
            throw new BadCredentialsException("Webhook signature verification failed");
        }
    }

    private ProviderWebhookPayload parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, ProviderWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed webhook payload", e);
        }
    }
}
