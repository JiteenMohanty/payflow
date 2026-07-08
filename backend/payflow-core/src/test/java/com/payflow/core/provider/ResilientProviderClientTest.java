package com.payflow.core.provider;

import com.payflow.core.common.exception.ProviderCommunicationException;
import com.payflow.core.common.provider.ProviderCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientProviderClientTest {

    @Mock
    private ProviderClient delegate;

    private CircuitBreaker circuitBreaker;
    private ResilientProviderClient client;

    @BeforeEach
    void setUp() {
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(ProviderCommunicationException.class)
                .build());
        circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(ProviderCommunicationException.class)
                .build());
        client = new ResilientProviderClient(delegate, retry, circuitBreaker);
    }

    @Test
    void retriesATransientFailureAndEventuallySucceeds() {
        ProviderAuthorizationRequest request = new ProviderAuthorizationRequest(new BigDecimal("10.00"), "USD", "ref-1");
        when(delegate.authorize(request))
                .thenThrow(new ProviderCommunicationException("transient"))
                .thenReturn(new ProviderAuthorizationResult(ProviderAuthorizationStatus.AUTHORIZED, "ch_1", null));

        ProviderAuthorizationResult result = client.authorize(request);

        assertThat(result.status()).isEqualTo(ProviderAuthorizationStatus.AUTHORIZED);
        verify(delegate, times(2)).authorize(request);
    }

    @Test
    void givesUpAfterMaxAttemptsAndSurfacesProviderCommunicationException() {
        ProviderAuthorizationRequest request = new ProviderAuthorizationRequest(new BigDecimal("10.00"), "USD", "ref-2");
        when(delegate.authorize(request)).thenThrow(new ProviderCommunicationException("still down"));

        assertThatThrownBy(() -> client.authorize(request)).isInstanceOf(ProviderCommunicationException.class);

        verify(delegate, times(3)).authorize(request);
    }

    @Test
    void aSuccessfulCallIsNeverRetried() {
        ProviderCaptureRequest request = new ProviderCaptureRequest("ch_1", new BigDecimal("10.00"), "USD");
        when(delegate.capture(request)).thenReturn(new ProviderCaptureResult(ProviderCaptureStatus.CAPTURED, null));

        client.capture(request);

        verify(delegate, times(1)).capture(request);
    }

    @Test
    void aBusinessDeclineIsNotRetriedSinceItIsAReturnValueNotAnException() {
        ProviderAuthorizationRequest request = new ProviderAuthorizationRequest(new BigDecimal("10.00"), "USD", "ref-3");
        when(delegate.authorize(request))
                .thenReturn(new ProviderAuthorizationResult(ProviderAuthorizationStatus.DECLINED, null, "insufficient_funds"));

        ProviderAuthorizationResult result = client.authorize(request);

        assertThat(result.status()).isEqualTo(ProviderAuthorizationStatus.DECLINED);
        verify(delegate, times(1)).authorize(request);
    }

    @Test
    void theCircuitOpensAfterEnoughFailuresAndThenFailsFastWithoutCallingTheDelegateAgain() {
        ProviderCaptureRequest request = new ProviderCaptureRequest("ch_1", new BigDecimal("10.00"), "USD");
        when(delegate.capture(request)).thenThrow(new ProviderCommunicationException("down"));

        // Bounded, not a fixed count: exactly which call trips the sliding
        // window's threshold is a Resilience4j internal detail, not
        // something this test should assume down to the exact iteration.
        for (int i = 0; i < 10 && circuitBreaker.getState() != CircuitBreaker.State.OPEN; i++) {
            try {
                client.capture(request);
            } catch (ProviderCommunicationException ignored) {
                // expected every iteration until the circuit actually opens
            }
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // CallNotPermittedException can only originate from the circuit
        // breaker denying permission before the delegate (and its retry
        // cycle) is ever reached - asserting the cause chain proves the
        // delegate was skipped without needing to count invocations.
        assertThatThrownBy(() -> client.capture(request))
                .isInstanceOf(ProviderCommunicationException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void providerCodeIsNeverSubjectToRetryOrTheCircuitBreaker() {
        when(delegate.providerCode()).thenReturn(ProviderCode.MOCK);

        assertThat(client.providerCode()).isEqualTo(ProviderCode.MOCK);
        verify(delegate, times(1)).providerCode();
    }
}
