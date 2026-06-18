package com.vdt.soc.tenant.dto;

import com.vdt.soc.common.model.enumeration.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponse {

    private UUID id;
    private String name;
    private String email;
    private String company;
    private String phone;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
