package com.payflow.core.security.api;

import com.payflow.core.organization.application.AuthenticatedUser;
import com.payflow.core.organization.application.UserAuthenticationService;
import com.payflow.core.security.jwt.JwtProperties;
import com.payflow.core.security.jwt.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthenticationService userAuthenticationService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = userAuthenticationService.authenticate(request.email(), request.password())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return issueTokens(user);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        UUID userId = jwtService.validateRefreshToken(request.refreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        AuthenticatedUser user = userAuthenticationService.getById(userId);
        return issueTokens(user);
    }

    private LoginResponse issueTokens(AuthenticatedUser user) {
        String accessToken = jwtService.issueAccessToken(user.userId(), user.email());
        String refreshToken = jwtService.issueRefreshToken(user.userId(), user.email());
        return new LoginResponse(accessToken, refreshToken, jwtProperties.accessTokenTtlMinutes() * 60);
    }
}
