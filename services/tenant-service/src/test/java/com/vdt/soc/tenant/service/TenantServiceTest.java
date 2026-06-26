package com.vdt.soc.tenant.service;

import com.vdt.soc.common.core.enumeration.TenantStatus;
import com.vdt.soc.common.core.enumeration.UserRole;
import com.vdt.soc.tenant.dto.CreateTenantRequest;
import com.vdt.soc.tenant.dto.CreateTenantResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantApiKeyRepository apiKeyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApiKeyGenerator apiKeyGenerator;
    @Mock
    private EtcdApiKeyPublisher etcdApiKeyPublisher;

    @InjectMocks
    private TenantService tenantService;

    private CreateTenantRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = CreateTenantRequest.builder()
                .name("Acme Corp")
                .email("admin@acme.com")
                .company("Acme")
                .phone("+84123456789")
                .adminUsername("acme_admin")
                .adminPassword("StrongP@ss123")
                .adminFullName("Acme Admin")
                .build();
    }

    @Test
    void createTenant_persistsTenantUserAndApiKey() {
        when(tenantRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(createRequest.getAdminUsername())).thenReturn(false);
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(createRequest.getAdminPassword())).thenReturn("hashed-pw");
        when(apiKeyGenerator.generateRawKey()).thenReturn("eps_raw-key");
        when(apiKeyGenerator.hash("eps_raw-key")).thenReturn("hashed-key");

        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(tenantId);
            return t;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(apiKeyRepository.save(any(TenantApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTenantResponse response = tenantService.createTenant(createRequest);

        assertThat(response.getApiKey()).isEqualTo("eps_raw-key");
        assertThat(response.getAdminUsername()).isEqualTo("acme_admin");
        assertThat(response.getTenant().getId()).isEqualTo(tenantId);
        assertThat(response.getTenant().getEmail()).isEqualTo("admin@acme.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.TENANT_ADMIN);
        assertThat(savedUser.getTenantId()).isEqualTo(tenantId);
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-pw");

        ArgumentCaptor<TenantApiKey> keyCaptor = ArgumentCaptor.forClass(TenantApiKey.class);
        verify(apiKeyRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getApiKeyHash()).isEqualTo("hashed-key");
        assertThat(keyCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createTenant_failsWhenEmailExists() {
        when(tenantRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(createRequest))
                .isInstanceOf(DuplicateResourceException.class);
        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createTenant_failsWhenUsernameExists() {
        when(tenantRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(createRequest.getAdminUsername())).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(createRequest))
                .isInstanceOf(DuplicateResourceException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void getTenant_returnsResponseWhenFound() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Acme")
                .email("a@b.com")
                .status(TenantStatus.ACTIVE)
                .build();
        tenant.setId(id);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        var response = tenantService.getTenant(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void getTenant_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTenant_appliesPartialUpdates() {
        UUID id = UUID.randomUUID();
        Tenant existing = Tenant.builder()
                .name("Old")
                .email("old@x.com")
                .status(TenantStatus.ACTIVE)
                .build();
        existing.setId(id);
        when(tenantRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantRequest req = UpdateTenantRequest.builder()
                .name("New")
                .status(TenantStatus.SUSPENDED)
                .build();

        var response = tenantService.updateTenant(id, req);

        assertThat(response.getName()).isEqualTo("New");
        assertThat(response.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(response.getEmail()).isEqualTo("old@x.com");
    }

    @Test
    void deleteTenant_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> tenantService.deleteTenant(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
