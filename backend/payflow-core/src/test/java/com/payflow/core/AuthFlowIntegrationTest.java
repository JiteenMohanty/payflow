package com.payflow.core;

import com.payflow.core.merchant.api.CreateMerchantRequest;
import com.payflow.core.merchant.api.CreateProviderAccountRequest;
import com.payflow.core.merchant.api.MerchantResponse;
import com.payflow.core.merchant.api.ProviderAccountResponse;
import com.payflow.core.common.provider.ProviderCode;
import com.payflow.core.organization.api.CreateApiKeyRequest;
import com.payflow.core.organization.api.CreateApiKeyResponse;
import com.payflow.core.organization.api.CreateOrganizationRequest;
import com.payflow.core.organization.api.CreateOrganizationResponse;
import com.payflow.core.organization.domain.ApiKeyEnvironment;
import com.payflow.core.security.api.LoginRequest;
import com.payflow.core.security.api.LoginResponse;
import com.payflow.core.security.api.RefreshRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of M1: signup, login, refresh, API key lifecycle,
 * organization RBAC, and merchant/provider-account creation - exercised over
 * real HTTP against a real Postgres, not mocks.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private UUID organizationId;
    private String ownerEmail;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void signUpAndLogIn() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ownerEmail = "owner-" + suffix + "@example.com";
        String ownerPassword = "correct-horse-battery-staple";

        CreateOrganizationRequest signupRequest =
                new CreateOrganizationRequest("Acme " + suffix, ownerEmail, "Ada Owner", ownerPassword);
        ResponseEntity<CreateOrganizationResponse> signupResponse =
                restTemplate.postForEntity("/v1/organizations", signupRequest, CreateOrganizationResponse.class);
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        organizationId = signupResponse.getBody().organizationId();

        LoginResponse login = login(ownerEmail, ownerPassword);
        accessToken = login.accessToken();
        refreshToken = login.refreshToken();
    }

    @Test
    void duplicateSignupEmailIsRejected() {
        CreateOrganizationRequest duplicate =
                new CreateOrganizationRequest("Another Org", ownerEmail, "Someone Else", "another-password");

        ResponseEntity<String> response = restTemplate.postForEntity("/v1/organizations", duplicate, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/v1/auth/login", new LoginRequest(ownerEmail, "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void organizationEndpointRejectsUnauthenticatedRequests() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/v1/organizations/" + organizationId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ownerCanFetchTheirOwnOrganization() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/organizations/" + organizationId, HttpMethod.GET, authed(accessToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(organizationId.toString());
    }

    @Test
    void userCannotFetchAnOrganizationTheyAreNotAMemberOf() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CreateOrganizationRequest otherOrgRequest = new CreateOrganizationRequest(
                "Other Org " + suffix, "other-" + suffix + "@example.com", "Other Owner", "another-password-1234");
        UUID otherOrganizationId = restTemplate
                .postForEntity("/v1/organizations", otherOrgRequest, CreateOrganizationResponse.class)
                .getBody().organizationId();

        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/organizations/" + otherOrganizationId, HttpMethod.GET, authed(accessToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshTokenIssuesNewAccessToken() {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/v1/auth/refresh", new RefreshRequest(refreshToken), LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void apiKeyLifecycleAndCrossPrincipalRejection() {
        ResponseEntity<CreateApiKeyResponse> createResponse = restTemplate.exchange(
                "/v1/organizations/" + organizationId + "/api-keys", HttpMethod.POST,
                authed(accessToken, new CreateApiKeyRequest(ApiKeyEnvironment.TEST)), CreateApiKeyResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = createResponse.getBody().apiKey();
        assertThat(apiKey).startsWith("pf_test_");

        ResponseEntity<String> listResponse = restTemplate.exchange(
                "/v1/organizations/" + organizationId + "/api-keys", HttpMethod.GET, authed(accessToken), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).doesNotContain(apiKey);

        // Valid API key, but not a dashboard session - must not reach a user-only endpoint.
        ResponseEntity<String> viaApiKey = restTemplate.exchange(
                "/v1/organizations/" + organizationId, HttpMethod.GET, authed(apiKey), String.class);
        assertThat(viaApiKey.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void merchantAndProviderAccountDefaultFlipsCorrectly() {
        ResponseEntity<MerchantResponse> merchantResponse = restTemplate.exchange(
                "/v1/organizations/" + organizationId + "/merchants", HttpMethod.POST,
                authed(accessToken, new CreateMerchantRequest("Test Merchant", "USD")), MerchantResponse.class);
        assertThat(merchantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID merchantId = merchantResponse.getBody().id();

        String providerAccountsPath =
                "/v1/organizations/" + organizationId + "/merchants/" + merchantId + "/provider-accounts";

        ResponseEntity<ProviderAccountResponse> first = restTemplate.exchange(
                providerAccountsPath, HttpMethod.POST,
                authed(accessToken, new CreateProviderAccountRequest(ProviderCode.MOCK, "{\"webhookSecret\":\"s1\"}", true)),
                ProviderAccountResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().isDefault()).isTrue();

        ResponseEntity<ProviderAccountResponse> second = restTemplate.exchange(
                providerAccountsPath, HttpMethod.POST,
                authed(accessToken, new CreateProviderAccountRequest(ProviderCode.MOCK, "{\"webhookSecret\":\"s2\"}", true)),
                ProviderAccountResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().isDefault()).isTrue();

        ResponseEntity<ProviderAccountResponse[]> listResponse = restTemplate.exchange(
                providerAccountsPath, HttpMethod.GET, authed(accessToken), ProviderAccountResponse[].class);
        assertThat(listResponse.getBody()).hasSize(2);
        long defaultCount = Arrays.stream(listResponse.getBody()).filter(ProviderAccountResponse::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
    }

    private LoginResponse login(String email, String password) {
        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity("/v1/auth/login", new LoginRequest(email, password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpEntity<Void> authed(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> authed(String bearerToken, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return new HttpEntity<>(body, headers);
    }
}
