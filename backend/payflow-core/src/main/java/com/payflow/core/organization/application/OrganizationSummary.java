package com.payflow.core.organization.application;

import com.payflow.core.organization.domain.OrganizationStatus;

import java.util.UUID;

public record OrganizationSummary(UUID id, String name, String slug, OrganizationStatus status) {
}
