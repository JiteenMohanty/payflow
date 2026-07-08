package com.payflow.mockprovider.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChaosFilterTest {

    @Mock
    private Random random;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @Test
    void aFailureRollBelowTheConfiguredRateShortCircuitsWithA503AndNeverReachesTheChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/provider/v1/charges");
        when(random.nextDouble()).thenReturn(0.3);
        ChaosFilter filter = new ChaosFilter(
                new ChaosProperties(false, 0, 0, true, 0.5), random);

        filter.doFilter(request, response, filterChain);

        verify(response).sendError(anyInt(), org.mockito.ArgumentMatchers.anyString());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void aFailureRollAtOrAboveTheConfiguredRateLetsTheRequestThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/provider/v1/charges");
        when(random.nextDouble()).thenReturn(0.8);
        ChaosFilter filter = new ChaosFilter(
                new ChaosProperties(false, 0, 0, true, 0.5), random);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void failureDisabledAlwaysLetsTheRequestThroughRegardlessOfTheRoll() throws Exception {
        when(request.getRequestURI()).thenReturn("/provider/v1/charges");
        ChaosFilter filter = new ChaosFilter(
                new ChaosProperties(false, 0, 0, false, 1.0), random);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(random, never()).nextDouble();
    }

    @Test
    void latencyEnabledActuallyDelaysBeforeContinuing() throws Exception {
        when(request.getRequestURI()).thenReturn("/provider/v1/charges");
        when(random.nextDouble()).thenReturn(1.0);
        ChaosFilter filter = new ChaosFilter(
                new ChaosProperties(true, 20, 40, false, 0.0), random);

        long start = System.nanoTime();
        filter.doFilter(request, response, filterChain);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        verify(filterChain).doFilter(request, response);
        org.assertj.core.api.Assertions.assertThat(elapsedMs).isGreaterThanOrEqualTo(20);
    }

    @Test
    void nonProviderPathsAreNeverTouchedByChaos() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        ChaosFilter filter = new ChaosFilter(
                new ChaosProperties(true, 1000, 2000, true, 1.0), random);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(random, never()).nextDouble();
    }
}
