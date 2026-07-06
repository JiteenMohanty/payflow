package com.payflow.core.organization.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 255) String organizationName,
        @NotBlank @Email @Size(max = 255) String ownerEmail,
        @NotBlank @Size(max = 255) String ownerFullName,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
