package com.payflow.core.security.filter;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.organization.application.ApiKeyPrincipal;
import com.payflow.core.organization.application.ApiKeyValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates merchant-facing API calls carrying {@code Authorization: Bearer pf_<env>_<secret>}.
 * Runs on every request but only acts on tokens with the pf_ prefix, leaving
 * JWT-based dashboard auth to {@link JwtAuthenticationFilter}.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "pf_";

    private final ApiKeyValidationService apiKeyValidationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (token.startsWith(API_KEY_PREFIX)) {
                Optional<ApiKeyPrincipal> principal = apiKeyValidationService.validate(token);
                if (principal.isEmpty()) {
                    throw new BadCredentialsException("Invalid API key");
                }
                authenticate(principal.get());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(ApiKeyPrincipal principal) {
        TenantContext tenantContext = new TenantContext(
                principal.organizationId(),
                PrincipalType.API_KEY,
                principal.apiKeyId(),
                principal.environment().name(),
                Set.of());

        PreAuthenticatedAuthenticationToken authToken =
                new PreAuthenticatedAuthenticationToken(principal.apiKeyId(), null, Set.of());
        authToken.setDetails(tenantContext);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
