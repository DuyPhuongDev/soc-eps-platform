package com.vdt.soc.tenant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.common.core.enumeration.UserRole;
import com.vdt.soc.common.core.enumeration.UserStatus;
import com.vdt.soc.tenant.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private UUID tenantId;
    private String tenantName;

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .tenantId(user.getTenant() != null? user.getTenant().getId(): null)
                .tenantName(user.getTenant() != null? user.getTenant().getName(): null)
                .build();
    }
}
