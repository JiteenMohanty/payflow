package com.payflow.core.webhook.inbound.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.payment.application.PaymentReconciliationSupport;
import com.payflow.core.security.hmac.HmacSha256Signer;
import com.payflow.core.webhook.inbound.persistence.InboundWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundWebhookServiceTest {

    private static final String SECRET = "test-secret";

    @Mock
    private InboundWebhookEventRepository repository;
    @Mock
    private PaymentReconciliationSupport paymentReconciliationSupport;

    private InboundWebhookService service;
    private final HmacSha256Signer signer = new HmacSha256Signer();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new InboundWebhookService(repository, paymentReconciliationSupport, signer, objectMapper);
        ReflectionTestUtils.setField(service, "mockProviderWebhookSecret", SECRET);
    }

    @Test
    void aValidCaptureEventIsRecordedAndReconciled() {
        String body = capturedBody("evt_1", "mock_ch_abc", "50.00");

        service.process(ProviderCode.MOCK, body, header(body));

        verify(repository).save(any());
        verify(paymentReconciliationSupport).reconcileCaptureConfirmation(
                ProviderCode.MOCK, "mock_ch_abc", new BigDecimal("50.00"), "USD");
    }

    @Test
    void anAuthorizedEventIsRecordedButNotReconciled() {
        String body = bodyFor("evt_2", "charge.authorized", "mock_ch_abc", "50.00");

        service.process(ProviderCode.MOCK, body, header(body));

        verify(repository).save(any());
        verify(paymentReconciliationSupport, never()).reconcileCaptureConfirmation(any(), any(), any(), any());
    }

    @Test
    void aDuplicateEventIsANoOpAndNeverReconciled() {
        String body = capturedBody("evt_3", "mock_ch_abc", "50.00");
        when(repository.existsByProviderCodeAndProviderEventId(ProviderCode.MOCK, "evt_3")).thenReturn(true);

        service.process(ProviderCode.MOCK, body, header(body));

        verify(repository, never()).save(any());
        verify(paymentReconciliationSupport, never()).reconcileCaptureConfirmation(any(), any(), any(), any());
    }

    @Test
    void aTamperedBodyFailsSignatureVerificationAndPersistsNothing() {
        String body = capturedBody("evt_4", "mock_ch_abc", "50.00");
        String validHeader = header(body);
        String tamperedBody = capturedBody("evt_4", "mock_ch_abc", "999999.00");

        assertThatThrownBy(() -> service.process(ProviderCode.MOCK, tamperedBody, validHeader))
                .isInstanceOf(BadCredentialsException.class);

        verify(repository, never()).save(any());
        verify(paymentReconciliationSupport, never()).reconcileCaptureConfirmation(any(), any(), any(), any());
    }

    @Test
    void aMalformedSignatureHeaderIsRejected() {
        String body = capturedBody("evt_5", "mock_ch_abc", "50.00");

        assertThatThrownBy(() -> service.process(ProviderCode.MOCK, body, "not-a-valid-header"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void aStaleTimestampOutsideToleranceIsRejected() {
        String body = capturedBody("evt_6", "mock_ch_abc", "50.00");
        long staleTimestamp = Instant.now().getEpochSecond() - 3600;
        String digest = signer.sign(SECRET, staleTimestamp + "." + body);

        assertThatThrownBy(() -> service.process(ProviderCode.MOCK, body, "t=" + staleTimestamp + ",v1=" + digest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void anUnsupportedProviderIsRejected() {
        String body = capturedBody("evt_7", "mock_ch_abc", "50.00");

        assertThatThrownBy(() -> service.process(ProviderCode.STRIPE, body, header(body)))
                .isInstanceOf(BadCredentialsException.class);

        verify(repository, never()).save(any());
    }

    private String capturedBody(String eventId, String chargeId, String amount) {
        return bodyFor(eventId, "charge.captured", chargeId, amount);
    }

    private String bodyFor(String eventId, String eventType, String chargeId, String amount) {
        try {
            return objectMapper.writeValueAsString(new ProviderWebhookPayload(
                    eventId, eventType, chargeId, new BigDecimal(amount), "USD", Instant.now()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String header(String body) {
        long ts = Instant.now().getEpochSecond();
        String digest = signer.sign(SECRET, ts + "." + body);
        return "t=" + ts + ",v1=" + digest;
    }
}
