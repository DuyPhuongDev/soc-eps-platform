package com.vdt.soc.tenant.dto;

import com.vdt.soc.common.enumeration.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresInSeconds;
    private UUID userId;
    private UUID tenantId;
    private UserRole role;
    private String username;
}
