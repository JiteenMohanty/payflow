package com.payflow.core.organization.application;

import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.organization.domain.ApiKey;
import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.organization.domain.ApiKeyStatus;
import com.payflow.core.organization.domain.Organization;
import com.payflow.core.organization.persistence.ApiKeyRepository;
import com.payflow.core.organization.persistence.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService implements ApiKeyValidationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String LIVE_PREFIX = "pf_live_";
    private static final String TEST_PREFIX = "pf_test_";
    private static final int LOOKUP_PREFIX_LENGTH = 8;
    private static final int SECRET_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateApiKeyResult createApiKey(UUID organizationId, ApiKeyEnvironment environment) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));

        String random = generateRandomSecret();
        String literalPrefix = literalPrefixFor(environment);
        String fullKey = literalPrefix + random;
        String lookupPrefix = literalPrefix + random.substring(0, LOOKUP_PREFIX_LENGTH);

        ApiKey apiKey = new ApiKey(organization, lookupPrefix, passwordEncoder.encode(random), environment);
        apiKeyRepository.save(apiKey);

        return new CreateApiKeyResult(apiKey.getId(), fullKey, lookupPrefix, environment);
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listApiKeys(UUID organizationId) {
        return apiKeyRepository.findByOrganizationId(organizationId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID organizationId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .filter(key -> key.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + apiKeyId));
        apiKey.revoke();
    }

    @Override
    @Transactional
    public Optional<ApiKeyPrincipal> validate(String presentedApiKey) {
        if (presentedApiKey == null) {
            return Optional.empty();
        }

        ApiKeyEnvironment environment;
        String literalPrefix;
        if (presentedApiKey.startsWith(LIVE_PREFIX)) {
            environment = ApiKeyEnvironment.LIVE;
            literalPrefix = LIVE_PREFIX;
        } else if (presentedApiKey.startsWith(TEST_PREFIX)) {
            environment = ApiKeyEnvironment.TEST;
            literalPrefix = TEST_PREFIX;
        } else {
            return Optional.empty();
        }

        String random = presentedApiKey.substring(literalPrefix.length());
        if (random.length() < LOOKUP_PREFIX_LENGTH) {
            return Optional.empty();
        }
        String lookupPrefix = literalPrefix + random.substring(0, LOOKUP_PREFIX_LENGTH);

        return apiKeyRepository.findByKeyPrefix(lookupPrefix)
                .filter(apiKey -> apiKey.getStatus() == ApiKeyStatus.ACTIVE)
                .filter(apiKey -> passwordEncoder.matches(random, apiKey.getHashedSecret()))
                .map(apiKey -> {
                    apiKey.recordUsage();
                    return new ApiKeyPrincipal(apiKey.getId(), apiKey.getOrganization().getId(), apiKey.getEnvironment());
                });
    }

    private String literalPrefixFor(ApiKeyEnvironment environment) {
        return environment == ApiKeyEnvironment.LIVE ? LIVE_PREFIX : TEST_PREFIX;
    }

    private String generateRandomSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiKeySummary toSummary(ApiKey apiKey) {
        return new ApiKeySummary(
                apiKey.getId(),
                apiKey.getKeyPrefix(),
                apiKey.getEnvironment(),
                apiKey.getStatus(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt());
    }
}
