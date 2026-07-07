package com.payflow.core.webhook.outbound.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateWebhookEndpointRequest(
        @NotBlank String url,
        @NotEmpty List<String> subscribedEvents
) {
}
