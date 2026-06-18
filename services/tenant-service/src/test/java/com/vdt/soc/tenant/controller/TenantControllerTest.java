package com.vdt.soc.tenant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.tenant.config.SecurityConfig;
import com.vdt.soc.common.security.JwtAuthenticationFilter;
import com.vdt.soc.tenant.dto.CreateTenantRequest;
import com.vdt.soc.tenant.dto.CreateTenantResponse;
import com.vdt.soc.tenant.dto.TenantResponse;
import com.vdt.soc.common.model.enumeration.TenantStatus;
import com.vdt.soc.tenant.security.TenantJwtUtil;
import com.vdt.soc.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
class TenantControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TenantService tenantService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private TenantJwtUtil tenantJwtUtil;

    @Test
    void createTenant_returns401WhenUnauthenticated() throws Exception {
        CreateTenantRequest request = validRequest();
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void createTenant_returns403WhenNotSystemAdmin() throws Exception {
        CreateTenantRequest request = validRequest();
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void createTenant_returns201WhenSystemAdmin() throws Exception {
        CreateTenantRequest request = validRequest();
        UUID tenantId = UUID.randomUUID();
        CreateTenantResponse response = CreateTenantResponse.builder()
                .apiKey("eps_raw")
                .adminUsername("acme_admin")
                .tenant(TenantResponse.builder()
                        .id(tenantId)
                        .name("Acme")
                        .email("admin@acme.com")
                        .status(TenantStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build())
                .build();

        when(tenantService.createTenant(any(CreateTenantRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value("eps_raw"))
                .andExpect(jsonPath("$.tenant.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.tenant.email").value("admin@acme.com"));
    }

    @Test
    @WithMockUser(roles = "TENANT_VIEWER")
    void listTenants_returnsListWhenAuthenticated() throws Exception {
        when(tenantService.listTenants()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void createTenant_returns400OnValidationError() throws Exception {
        CreateTenantRequest invalid = CreateTenantRequest.builder()
                .name("").email("not-an-email").adminUsername("").adminPassword("").build();

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    private CreateTenantRequest validRequest() {
        return CreateTenantRequest.builder()
                .name("Acme")
                .email("admin@acme.com")
                .company("Acme")
                .phone("+84")
                .adminUsername("acme_admin")
                .adminPassword("StrongP@ss123")
                .adminFullName("Acme Admin")
                .build();
    }
}
