package com.payflow.core.organization.application;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthenticationService {

    Optional<AuthenticatedUser> authenticate(String email, String rawPassword);

    AuthenticatedUser getById(UUID userId);
}
