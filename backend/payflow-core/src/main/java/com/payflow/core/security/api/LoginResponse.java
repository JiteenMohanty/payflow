package com.payflow.core.security.api;

public record LoginResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
