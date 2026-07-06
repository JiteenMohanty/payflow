package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.ApiKey;
import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.organization.domain.Organization;
import com.payflow.core.organization.persistence.ApiKeyRepository;
import com.payflow.core.organization.persistence.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, organizationRepository, passwordEncoder);
    }

    @Test
    void createdKeyRoundTripsThroughValidation() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organizationWithId(organizationId);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        ArgumentCaptor<ApiKey> savedCaptor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateApiKeyResult result = apiKeyService.createApiKey(organizationId, ApiKeyEnvironment.TEST);

        assertThat(result.fullKey()).startsWith("pf_test_");
        assertThat(result.fullKey()).startsWith(result.keyPrefix());

        when(apiKeyRepository.findByKeyPrefix(result.keyPrefix())).thenReturn(Optional.of(savedCaptor.getValue()));

        Optional<ApiKeyPrincipal> validated = apiKeyService.validate(result.fullKey());

        assertThat(validated).isPresent();
        assertThat(validated.get().organizationId()).isEqualTo(organizationId);
        assertThat(validated.get().environment()).isEqualTo(ApiKeyEnvironment.TEST);
    }

    @Test
    void rejectsIncorrectSecretForAKnownPrefix() {
        Organization organization = organizationWithId(UUID.randomUUID());
        ApiKey stored = new ApiKey(organization, "pf_live_AAAAAAAA", passwordEncoder.encode("the-real-secret-value"), ApiKeyEnvironment.LIVE);
        when(apiKeyRepository.findByKeyPrefix("pf_live_AAAAAAAA")).thenReturn(Optional.of(stored));

        Optional<ApiKeyPrincipal> validated = apiKeyService.validate("pf_live_AAAAAAAA-but-a-different-secret");

        assertThat(validated).isEmpty();
    }

    @Test
    void rejectsRevokedKey() {
        Organization organization = organizationWithId(UUID.randomUUID());
        ApiKey stored = new ApiKey(organization, "pf_live_BBBBBBBB", passwordEncoder.encode("the-real-secret-value"), ApiKeyEnvironment.LIVE);
        stored.revoke();
        when(apiKeyRepository.findByKeyPrefix("pf_live_BBBBBBBB")).thenReturn(Optional.of(stored));

        Optional<ApiKeyPrincipal> validated = apiKeyService.validate("pf_live_BBBBBBBBthe-real-secret-value");

        assertThat(validated).isEmpty();
    }

    @Test
    void rejectsMalformedKeyWithoutLookingAtTheRepository() {
        assertThat(apiKeyService.validate("not-a-payflow-key")).isEmpty();
        assertThat(apiKeyService.validate(null)).isEmpty();
    }

    private Organization organizationWithId(UUID id) {
        Organization organization = new Organization("Acme Inc", "acme-inc");
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }
}
