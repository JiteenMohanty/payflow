package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void acceptsAPublicHttpsUrl() {
        assertThatCode(() -> validator.validate("https://example.com/hooks/payflow")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttp() {
        assertThatThrownBy(() -> validator.validate("http://example.com/hooks"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsLoopbackAddressesAndHostnames() {
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/hooks"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> validator.validate("https://localhost/hooks"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsRfc1918PrivateAddresses() {
        assertThatThrownBy(() -> validator.validate("https://10.0.0.5/hooks"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> validator.validate("https://192.168.1.5/hooks"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> validator.validate("https://172.16.0.5/hooks"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsLinkLocalAddresses() {
        assertThatThrownBy(() -> validator.validate("https://169.254.1.1/hooks"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsAMalformedUrl() {
        assertThatThrownBy(() -> validator.validate("not a url"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsAUrlWithNoHost() {
        assertThatThrownBy(() -> validator.validate("https:///hooks"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsAnUnresolvableHost() {
        // .invalid is IANA-reserved (RFC 2606) to never resolve.
        assertThatThrownBy(() -> validator.validate("https://payflow-test-host.invalid/hooks"))
                .isInstanceOf(DomainValidationException.class);
    }
}
