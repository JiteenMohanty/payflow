package com.payflow.core;

import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.merchant.api.CreateMerchantRequest;
import com.payflow.core.merchant.api.CreateProviderAccountRequest;
import com.payflow.core.merchant.api.MerchantResponse;
import com.payflow.core.organization.api.CreateApiKeyRequest;
import com.payflow.core.organization.api.CreateApiKeyResponse;
import com.payflow.core.organization.api.CreateOrganizationRequest;
import com.payflow.core.organization.api.CreateOrganizationResponse;
import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.security.api.LoginRequest;
import com.payflow.core.security.api.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Shared Postgres + Redis containers for every full-context test, plus a
 * reusable tenant provisioning helper (signup -> login -> merchant ->
 * default MOCK provider account -> API key) so each integration test doesn't
 * re-implement that setup. Containers are declared as static fields on this
 * common base (rather than duplicated per test class) so Testcontainers
 * reuses the same running containers across the whole suite instead of
 * restarting them per class - the standard pattern for a class-hierarchy of
 * Testcontainers-backed tests.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    protected Tenant provisionTenant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + suffix + "@example.com";
        String password = "correct-horse-battery-staple";

        CreateOrganizationResponse signup = restTemplate.postForEntity(
                "/v1/organizations", new CreateOrganizationRequest("Acme " + suffix, email, "Ada Owner", password),
                CreateOrganizationResponse.class).getBody();

        LoginResponse login = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(email, password), LoginResponse.class).getBody();

        HttpHeaders dashboardHeaders = new HttpHeaders();
        dashboardHeaders.setBearerAuth(login.accessToken());

        MerchantResponse merchant = restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/merchants", HttpMethod.POST,
                new HttpEntity<>(new CreateMerchantRequest("Test Merchant", "USD"), dashboardHeaders),
                MerchantResponse.class).getBody();

        restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/merchants/" + merchant.id() + "/provider-accounts",
                HttpMethod.POST,
                new HttpEntity<>(new CreateProviderAccountRequest(ProviderCode.MOCK, "{}", true), dashboardHeaders),
                Void.class);

        CreateApiKeyResponse apiKeyResponse = restTemplate.exchange(
                "/v1/organizations/" + signup.organizationId() + "/api-keys", HttpMethod.POST,
                new HttpEntity<>(new CreateApiKeyRequest(ApiKeyEnvironment.TEST), dashboardHeaders),
                CreateApiKeyResponse.class).getBody();

        return new Tenant(signup.organizationId(), merchant.id(), apiKeyResponse.apiKey(), login.accessToken());
    }

    protected record Tenant(UUID organizationId, UUID merchantId, String apiKey, String accessToken) {
    }
}
