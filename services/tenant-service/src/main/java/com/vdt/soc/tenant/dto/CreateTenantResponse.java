package com.vdt.soc.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantResponse {

    private TenantResponse tenant;
    private String apiKey;
    private String adminUsername;
}
