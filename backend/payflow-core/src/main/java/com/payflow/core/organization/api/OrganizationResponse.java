package com.payflow.core.organization.api;

import com.payflow.core.organization.domain.OrganizationStatus;

import java.util.UUID;

public record OrganizationResponse(UUID id, String name, String slug, OrganizationStatus status) {
}
