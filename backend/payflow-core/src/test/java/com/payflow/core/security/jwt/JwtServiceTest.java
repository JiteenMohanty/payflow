package com.payflow.core.security.jwt;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties properties =
            new JwtProperties("test-signing-secret-that-is-long-enough-for-hmac-sha256-1234567890", 15, 7);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void issuesAndValidatesAccessToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueAccessToken(userId, "owner@example.com");
        Optional<UUID> resolved = jwtService.validateAccessToken(token);

        assertThat(resolved).contains(userId);
    }

    @Test
    void issuesAndValidatesRefreshToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueRefreshToken(userId, "owner@example.com");
        Optional<UUID> resolved = jwtService.validateRefreshToken(token);

        assertThat(resolved).contains(userId);
    }

    @Test
    void rejectsRefreshTokenPresentedAsAccessToken() {
        UUID userId = UUID.randomUUID();

        String refreshToken = jwtService.issueRefreshToken(userId, "owner@example.com");

        assertThat(jwtService.validateAccessToken(refreshToken)).isEmpty();
    }

    @Test
    void rejectsAccessTokenPresentedAsRefreshToken() {
        UUID userId = UUID.randomUUID();

        String accessToken = jwtService.issueAccessToken(userId, "owner@example.com");

        assertThat(jwtService.validateRefreshToken(accessToken)).isEmpty();
    }

    @Test
    void rejectsTamperedToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "owner@example.com");
        // Flip a character in the middle of the signature rather than the very
        // last character: base64url's final character can carry unused padding
        // bits, so mutating it doesn't always change the decoded byte - flaky.
        int middle = token.length() / 2;
        char flipped = token.charAt(middle) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, middle) + flipped + token.substring(middle + 1);

        assertThat(jwtService.validateAccessToken(tampered)).isEmpty();
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.validateAccessToken("not-a-jwt")).isEmpty();
    }
}
