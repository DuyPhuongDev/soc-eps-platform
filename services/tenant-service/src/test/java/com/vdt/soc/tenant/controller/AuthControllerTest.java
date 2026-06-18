package com.vdt.soc.tenant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.tenant.config.SecurityConfig;
import com.vdt.soc.tenant.dto.LoginRequest;
import com.vdt.soc.tenant.dto.LoginResponse;
import com.vdt.soc.common.model.enumeration.UserRole;
import com.vdt.soc.common.security.JwtAuthenticationFilter;
import com.vdt.soc.tenant.exception.UnauthorizedException;
import com.vdt.soc.tenant.security.TenantJwtUtil;
import com.vdt.soc.tenant.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private TenantJwtUtil tenantJwtUtil;

    @Test
    void login_returnsTokenForValidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice").password("Secret123!").build();
        LoginResponse response = LoginResponse.builder()
                .accessToken("jwt").tokenType("Bearer").expiresInSeconds(3600)
                .userId(UUID.randomUUID()).tenantId(UUID.randomUUID())
                .role(UserRole.TENANT_ADMIN).username("alice").build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
    }

    @Test
    void login_returns400WhenValidationFails() throws Exception {
        LoginRequest request = LoginRequest.builder().username("").password("").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns401WhenCredentialsInvalid() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice").password("wrong").build();
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
