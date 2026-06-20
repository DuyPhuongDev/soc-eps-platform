package com.vdt.soc.common.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Cached API key mapping from tenant-service.
 * Maps SHA-256 hash → tenantId for collector authentication.
 * Single source of truth — used by tenant-service (publisher)
 * and collector-service (consumer).
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantApiKeyMapping {

    private UUID tenantId;
    private String apiKeyHash; // SHA-256 hex
}
