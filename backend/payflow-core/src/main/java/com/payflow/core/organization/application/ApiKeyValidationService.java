package com.payflow.core.organization.application;

import java.util.Optional;

public interface ApiKeyValidationService {

    Optional<ApiKeyPrincipal> validate(String presentedApiKey);
}
