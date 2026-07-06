package com.payflow.core.organization.application;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String fullName) {
}
