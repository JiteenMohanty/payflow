package com.payflow.core.security.filter;

import com.payflow.core.common.tenant.PrincipalType;
import com.payflow.core.common.tenant.TenantContext;
import com.payflow.core.security.jwt.JwtService;
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
import java.util.UUID;

/**
 * Authenticates admin dashboard calls carrying {@code Authorization: Bearer <jwt>}.
 * Skips tokens with the pf_ prefix, leaving those to {@link ApiKeyAuthenticationFilter}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "pf_";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (!token.startsWith(API_KEY_PREFIX)) {
                Optional<UUID> userId = jwtService.validateAccessToken(token);
                if (userId.isEmpty()) {
                    throw new BadCredentialsException("Invalid or expired token");
                }
                authenticate(userId.get());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(UUID userId) {
        TenantContext tenantContext = new TenantContext(null, PrincipalType.USER, userId, null, Set.of());

        PreAuthenticatedAuthenticationToken authToken = new PreAuthenticatedAuthenticationToken(userId, null, Set.of());
        authToken.setDetails(tenantContext);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
