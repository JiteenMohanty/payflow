package com.payflow.core.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates stateless JWTs. Refresh tokens are not persisted or
 * individually revocable in v1 - see ADR-0010; this is a deliberate v1
 * simplification, not a placeholder.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    public String issueAccessToken(UUID userId, String email) {
        return issueToken(userId, email, TOKEN_TYPE_ACCESS, Duration.ofMinutes(jwtProperties.accessTokenTtlMinutes()));
    }

    public String issueRefreshToken(UUID userId, String email) {
        return issueToken(userId, email, TOKEN_TYPE_REFRESH, Duration.ofDays(jwtProperties.refreshTokenTtlDays()));
    }

    public Optional<UUID> validateAccessToken(String token) {
        return parse(token, TOKEN_TYPE_ACCESS);
    }

    public Optional<UUID> validateRefreshToken(String token) {
        return parse(token, TOKEN_TYPE_REFRESH);
    }

    private String issueToken(UUID userId, String email, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey())
                .compact();
    }

    private Optional<UUID> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
