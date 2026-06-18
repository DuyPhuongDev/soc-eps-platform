package com.vdt.soc.license.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.license.entity.LicenseAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseAuditLogResponse {

    private Long id;
    private UUID licenseId;
    private UUID tenantId;
    private String action;
    private String changes;
    private String performedBy;
    private Instant createdAt;

    public static LicenseAuditLogResponse from(LicenseAuditLog log) {
        return LicenseAuditLogResponse.builder()
                .id(log.getId())
                .licenseId(log.getLicenseId())
                .tenantId(log.getTenantId())
                .action(log.getAction())
                .changes(log.getChanges())
                .performedBy(log.getPerformedBy())
                .createdAt(log.getCreatedAt())
                .build();
    }
}