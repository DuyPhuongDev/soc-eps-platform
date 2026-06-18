package com.vdt.soc.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String company;

    @Size(max = 50)
    private String phone;

    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 100)
    private String adminUsername;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, max = 100)
    private String adminPassword;

    @Size(max = 255)
    private String adminFullName;
}
