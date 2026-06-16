package com.vdt.soc.tenant.service;

import com.vdt.soc.common.enumeration.UserRole;
import com.vdt.soc.tenant.dto.CreateTenantRequest;
import com.vdt.soc.tenant.dto.CreateTenantResponse;
import com.vdt.soc.tenant.dto.TenantApiKeyMapping;
import com.vdt.soc.tenant.dto.TenantResponse;
import com.vdt.soc.tenant.dto.UpdateTenantRequest;
import com.vdt.soc.tenant.entity.Tenant;
import com.vdt.soc.tenant.entity.TenantApiKey;
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.tenant.enumeration.ApiKeyStatus;
import com.vdt.soc.tenant.enumeration.UserStatus;
import com.vdt.soc.tenant.exception.DuplicateResourceException;
import com.vdt.soc.tenant.exception.ResourceNotFoundException;
import com.vdt.soc.tenant.repository.TenantApiKeyRepository;
import com.vdt.soc.tenant.repository.TenantRepository;
import com.vdt.soc.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyGenerator apiKeyGenerator;

    @Transactional
    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Tenant email already exists: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getAdminUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getAdminUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User email already exists: " + request.getEmail());
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .email(request.getEmail())
                .company(request.getCompany())
                .phone(request.getPhone())
                .build();
        tenant = tenantRepository.save(tenant);

        User admin = User.builder()
                .username(request.getAdminUsername())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .email(request.getEmail())
                .fullName(request.getAdminFullName())
                .tenantId(tenant.getId())
                .role(UserRole.TENANT_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        String rawApiKey = apiKeyGenerator.generateRawKey();
        TenantApiKey apiKey = TenantApiKey.builder()
                .tenantId(tenant.getId())
                .apiKeyHash(apiKeyGenerator.hash(rawApiKey))
                .status(ApiKeyStatus.ACTIVE)
                .build();
        apiKeyRepository.save(apiKey);

        return CreateTenantResponse.builder()
                .tenant(toResponse(tenant))
                .apiKey(rawApiKey)
                .adminUsername(admin.getUsername())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(UUID id, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));

        if (request.getEmail() != null && !request.getEmail().equals(tenant.getEmail())) {
            if (tenantRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Tenant email already exists: " + request.getEmail());
            }
            tenant.setEmail(request.getEmail());
        }
        if (request.getName() != null) {
            tenant.setName(request.getName());
        }
        if (request.getCompany() != null) {
            tenant.setCompany(request.getCompany());
        }
        if (request.getPhone() != null) {
            tenant.setPhone(request.getPhone());
        }
        if (request.getStatus() != null) {
            tenant.setStatus(request.getStatus());
        }

        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional
    public void deleteTenant(UUID id) {
        if (!tenantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tenant not found: " + id);
        }
        tenantRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<TenantApiKeyMapping> listActiveApiKeyMappings() {
        return apiKeyRepository.findAllByStatus(ApiKeyStatus.ACTIVE).stream()
                .map(k -> TenantApiKeyMapping.builder()
                        .tenantId(k.getTenantId())
                        .apiKeyHash(k.getApiKeyHash())
                        .build())
                .toList();
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .email(tenant.getEmail())
                .company(tenant.getCompany())
                .phone(tenant.getPhone())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
