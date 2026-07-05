package com.vdt.soc.notification.dto;

import com.vdt.soc.common.core.enumeration.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Minimal DTO used to deserialize the tenant-service internal response
 * via Feign. Field names must match {@code TenantResponse} JSON keys
 * produced by tenant-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContactResponse {

    private UUID id;
    private String name;
    private String email;
    private TenantStatus status;
}
