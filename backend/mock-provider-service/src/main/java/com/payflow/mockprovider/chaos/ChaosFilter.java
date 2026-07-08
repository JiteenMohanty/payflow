package com.payflow.mockprovider.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Random;

/**
 * Simulates an external provider's real-world unreliability (EDD section
 * 5.3, M11) ahead of every /provider/v1/** request - ChargeController's own
 * handlers stay deterministic. Scoped away from actuator/health endpoints
 * deliberately: chaos there would be actively harmful, not realistic (a
 * liveness/readiness probe timing out because of simulated latency could get
 * a healthy instance killed by an orchestrator).
 *
 * response.sendError() (raw Servlet API), not a thrown ResponseStatusException:
 * a Filter runs ahead of DispatcherServlet, so Spring MVC's own exception
 * resolution never sees anything thrown from here.
 */
@Component
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);
    private static final String PROVIDER_PATH_PREFIX = "/provider/v1/";

    private final ChaosProperties properties;
    private final Random random;

    public ChaosFilter(ChaosProperties properties, Random random) {
        this.properties = properties;
        this.random = random;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROVIDER_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (properties.latencyEnabled()) {
            simulateLatency();
        }
        if (properties.failureEnabled() && random.nextDouble() < properties.failureRate()) {
            log.warn("Chaos filter simulating a provider outage for {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Simulated provider outage");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void simulateLatency() {
        long range = properties.latencyMaxMs() - properties.latencyMinMs();
        long delayMs = properties.latencyMinMs() + (range > 0 ? (long) (random.nextDouble() * range) : 0);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
