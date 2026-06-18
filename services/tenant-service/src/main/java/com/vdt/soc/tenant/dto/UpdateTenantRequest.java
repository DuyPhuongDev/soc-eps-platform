package com.vdt.soc.tenant.dto;

import com.vdt.soc.common.model.enumeration.TenantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTenantRequest {

    @Size(max = 255)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String company;

    @Size(max = 50)
    private String phone;

    private TenantStatus status;
}
