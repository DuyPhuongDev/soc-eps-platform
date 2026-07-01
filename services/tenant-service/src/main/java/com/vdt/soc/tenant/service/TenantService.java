package com.vdt.soc.tenant.service;

import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import com.vdt.soc.common.core.enumeration.ApiKeyStatus;
import com.vdt.soc.common.core.enumeration.UserRole;
import com.vdt.soc.common.core.enumeration.UserStatus;
import com.vdt.soc.tenant.dto.CreateTenantRequest;
import com.vdt.soc.tenant.dto.CreateTenantResponse;
import com.vdt.soc.tenant.dto.TenantResponse;
import com.vdt.soc.tenant.dto.UpdateTenantRequest;
import com.vdt.soc.tenant.entity.Tenant;
import com.vdt.soc.tenant.entity.TenantApiKey;
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.tenant.etcd.EtcdApiKeyPublisher;
import com.vdt.soc.tenant.exception.DuplicateResourceException;
import com.vdt.soc.tenant.exception.ResourceNotFoundException;
import com.vdt.soc.tenant.repository.TenantApiKeyRepository;
import com.vdt.soc.tenant.repository.TenantRepository;
import com.vdt.soc.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class
TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyGenerator apiKeyGenerator;
    private final EtcdApiKeyPublisher etcdApiKeyPublisher;

    @Transactional
    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        log.info("Creating tenant: name={}, email={}", request.getName(), request.getEmail());

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
                .tenant(tenant)
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

        // Publish to etcd for real-time collector sync
        etcdApiKeyPublisher.publish(tenant.getId(), apiKey.getApiKeyHash());

        log.info("Tenant created successfully: id={}, name={}", tenant.getId(), tenant.getName());
        return CreateTenantResponse.builder()
                .tenant(toResponse(tenant))
                .apiKey(rawApiKey)
                .adminUsername(admin.getUsername())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        log.debug("Listing all tenants");
        List<TenantResponse> tenants = tenantRepository.findAll().stream().map(this::toResponse).toList();
        log.debug("Found {} tenants", tenants.size());
        return tenants;
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID id) {
        log.debug("Getting tenant by id: {}", id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found: id={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });
        log.debug("Tenant found: id={}, name={}", tenant.getId(), tenant.getName());
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

        // Remove API keys from etcd before deleting
        List<TenantApiKeyMapping> mappings = listActiveApiKeyMappings();
        for (TenantApiKeyMapping m : mappings) {
            if (m.getTenantId().equals(id)) {
                etcdApiKeyPublisher.remove(m.getApiKeyHash());
            }
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
