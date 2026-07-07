package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.crypto.SymmetricEncryptor;
import com.payflow.core.common.exception.DomainValidationException;
import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.webhook.outbound.domain.WebhookEndpoint;
import com.payflow.core.webhook.outbound.domain.WebhookEndpointStatus;
import com.payflow.core.webhook.outbound.persistence.WebhookDeliveryRepository;
import com.payflow.core.webhook.outbound.persistence.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookEndpointServiceTest {

    @Mock
    private WebhookEndpointRepository endpointRepository;
    @Mock
    private WebhookDeliveryRepository deliveryRepository;
    @Mock
    private WebhookUrlValidator urlValidator;
    @Mock
    private SymmetricEncryptor encryptor;

    private WebhookEndpointService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WebhookEndpointService(endpointRepository, deliveryRepository, urlValidator, encryptor);
    }

    @Test
    void createEndpointValidatesTheUrlEncryptsTheSecretAndReturnsItOnce() {
        when(encryptor.encrypt(any())).thenReturn(new byte[]{1, 2, 3});
        when(endpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateWebhookEndpointResult result = service.createEndpoint(
                organizationId, "https://example.com/hooks", List.of("payment.captured"));

        verify(urlValidator).validate("https://example.com/hooks");
        assertThat(result.secret()).startsWith("whsec_");
        assertThat(result.endpoint().subscribedEvents()).containsExactly("payment.captured");
        assertThat(result.endpoint().status()).isEqualTo(WebhookEndpointStatus.ACTIVE);
    }

    @Test
    void createEndpointRejectsAnEmptySubscribedEventsList() {
        assertThatThrownBy(() -> service.createEndpoint(organizationId, "https://example.com/hooks", List.of()))
                .isInstanceOf(DomainValidationException.class);

        verify(endpointRepository, never()).save(any());
    }

    @Test
    void listEndpointsReturnsTheOrganizationsEndpoints() {
        WebhookEndpoint endpoint = newEndpoint();
        when(endpointRepository.findByOrganizationId(organizationId)).thenReturn(List.of(endpoint));

        List<WebhookEndpointSummary> summaries = service.listEndpoints(organizationId);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).url()).isEqualTo("https://example.com/hooks");
    }

    @Test
    void disableEndpointMarksItDisabled() {
        WebhookEndpoint endpoint = newEndpoint();
        when(endpointRepository.findByIdAndOrganizationId(endpoint.getId(), organizationId)).thenReturn(Optional.of(endpoint));

        service.disableEndpoint(organizationId, endpoint.getId());

        assertThat(endpoint.getStatus()).isEqualTo(WebhookEndpointStatus.DISABLED);
    }

    @Test
    void disableEndpointRejectsAnEndpointOutsideTheOrganization() {
        when(endpointRepository.findByIdAndOrganizationId(any(), eq(organizationId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disableEndpoint(organizationId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listDeliveriesRejectsAnEndpointOutsideTheOrganization() {
        when(endpointRepository.findByIdAndOrganizationId(any(), eq(organizationId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listDeliveries(organizationId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private WebhookEndpoint newEndpoint() {
        WebhookEndpoint endpoint = new WebhookEndpoint(
                organizationId, "https://example.com/hooks", new byte[]{1}, List.of("payment.captured"));
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        return endpoint;
    }
}
