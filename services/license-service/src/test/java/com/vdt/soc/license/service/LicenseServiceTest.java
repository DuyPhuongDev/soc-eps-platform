package com.vdt.soc.license.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vdt.soc.common.core.enumeration.LicenseMode;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import com.vdt.soc.common.core.enumeration.LicenseStatus;
import com.vdt.soc.license.dto.CreateLicenseRequest;
import com.vdt.soc.license.dto.LicenseResponse;
import com.vdt.soc.license.dto.PageResponse;
import com.vdt.soc.license.dto.UpdateLicenseRequest;
import com.vdt.soc.license.entity.License;
import com.vdt.soc.license.entity.LicenseAuditLog;
import com.vdt.soc.license.exception.LicenseNotFoundException;
import com.vdt.soc.license.mapper.LicenseMapper;
import com.vdt.soc.license.repository.LicenseAuditLogRepository;
import com.vdt.soc.license.repository.LicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepo;

    @Mock
    private LicenseAuditLogRepository auditLogRepo;

    @Mock
    private LicenseMapper licenseMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private LicenseService licenseService;

    private UUID tenantId;
    private UUID licenseId;
    private License starterLicense;
    private Instant now;
    private Instant startDate;
    private Instant endDate;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        licenseId = UUID.randomUUID();
        now = Instant.now();
        startDate = now.minus(30, ChronoUnit.DAYS);
        endDate = now.plus(365, ChronoUnit.DAYS);

        starterLicense = License.builder()
                .tenantId(tenantId)
                .epsQuota(LicensePlan.STARTER.getEpsQuota())
                .mode(LicensePlan.STARTER.getMode())
                .burstMultiplier(LicensePlan.STARTER.getBurstMultiplier())
                .plan(LicensePlan.STARTER)
                .startDate(startDate)
                .endDate(endDate)
                .status(LicenseStatus.ACTIVE)
                .build();
        starterLicense.setId(licenseId);

        // Default mapper stubs — delegate to real mapping logic
        lenient().when(licenseMapper.toEntity(any(CreateLicenseRequest.class))).thenAnswer(inv -> {
            CreateLicenseRequest req = inv.getArgument(0);
            LicensePlan plan = req.getPlan();
            return License.builder()
                    .tenantId(req.getTenantId())
                    .epsQuota(plan.getEpsQuota())
                    .mode(plan.getMode())
                    .burstMultiplier(plan.getBurstMultiplier())
                    .plan(plan)
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .status(LicenseStatus.ACTIVE)
                    .build();
        });
        lenient().when(licenseMapper.toResponse(any(License.class))).thenAnswer(inv ->
                LicenseResponse.from(inv.getArgument(0)));
        lenient().when(licenseMapper.toPageResponse(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Page<License> page = inv.getArgument(0);
            return PageResponse.fromPage(page.map(LicenseResponse::from));
        });
    }

    // ── createLicense ──

    @Test
    void createLicense_withStarterPlan_success() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.STARTER)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(licenseId);
            return l;
        });
        when(auditLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LicenseResponse response = licenseService.createLicense(request, "admin");

        assertThat(response.getPlan()).isEqualTo(LicensePlan.STARTER);
        assertThat(response.getEpsQuota()).isEqualTo(100);
        assertThat(response.getMode()).isEqualTo(LicenseMode.THROTTLE);
        assertThat(response.getBurstMultiplier()).isEqualTo(1.0);
        assertThat(response.getStatus()).isEqualTo(LicenseStatus.ACTIVE);

        verify(licenseRepo).save(any(License.class));
        verify(auditLogRepo).save(any(LicenseAuditLog.class));
    }

    @Test
    void createLicense_withProfessionalPlan_success() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.PROFESSIONAL)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(licenseId);
            return l;
        });

        LicenseResponse response = licenseService.createLicense(request, "admin");

        assertThat(response.getPlan()).isEqualTo(LicensePlan.PROFESSIONAL);
        assertThat(response.getEpsQuota()).isEqualTo(500);
        assertThat(response.getMode()).isEqualTo(LicenseMode.BURST_THEN_THROTTLE);
        assertThat(response.getBurstMultiplier()).isEqualTo(1.5);
    }

    @Test
    void createLicense_withEnterprisePlan_success() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.ENTERPRISE)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(licenseId);
            return l;
        });

        LicenseResponse response = licenseService.createLicense(request, "admin");

        assertThat(response.getPlan()).isEqualTo(LicensePlan.ENTERPRISE);
        assertThat(response.getEpsQuota()).isEqualTo(2000);
        assertThat(response.getMode()).isEqualTo(LicenseMode.OVERFLOW_BILLING);
        assertThat(response.getBurstMultiplier()).isEqualTo(2.0);
    }

    @Test
    void createLicense_failsWhenTenantAlreadyHasActiveLicense() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.STARTER)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.of(starterLicense));

        assertThatThrownBy(() -> licenseService.createLicense(request, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active license");

        verify(licenseRepo, never()).save(any());
    }

    // ── getLicense ──

    @Test
    void getLicense_success() {
        when(licenseRepo.findById(licenseId)).thenReturn(Optional.of(starterLicense));

        LicenseResponse response = licenseService.getLicense(licenseId);

        assertThat(response.getId()).isEqualTo(licenseId);
        assertThat(response.getPlan()).isEqualTo(LicensePlan.STARTER);
    }

    @Test
    void getLicense_throwsWhenNotFound() {
        when(licenseRepo.findById(licenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.getLicense(licenseId))
                .isInstanceOf(LicenseNotFoundException.class);
    }

    // ── listLicenses ──

    @Test
    void listLicenses_byTenantId() {
        Page<License> licensePage = new PageImpl<>(List.of(starterLicense), Pageable.unpaged(), 1);
        when(licenseRepo.findByTenantIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(licensePage);

        PageResponse<LicenseResponse> responses = licenseService.listLicenses(tenantId, Pageable.unpaged());

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void listAllLicenses() {
        Page<License> licensePage = new PageImpl<>(List.of(starterLicense), Pageable.unpaged(), 1);
        when(licenseRepo.findAll(any(Pageable.class))).thenReturn(licensePage);

        PageResponse<LicenseResponse> responses = licenseService.listAllLicenses(Pageable.unpaged());

        assertThat(responses.getContent()).hasSize(1);
    }

    // ── updateLicense ──

    @Test
    void updateLicense_success() {
        UpdateLicenseRequest request = UpdateLicenseRequest.builder()
                .epsQuota(2000)
                .build();

        when(licenseRepo.findById(licenseId)).thenReturn(Optional.of(starterLicense));
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LicenseResponse response = licenseService.updateLicense(licenseId, request, "admin");

        assertThat(response.getEpsQuota()).isEqualTo(2000);
        verify(auditLogRepo).save(any(LicenseAuditLog.class));
    }

    @Test
    void updateLicense_throwsWhenNotFound() {
        UpdateLicenseRequest request = UpdateLicenseRequest.builder().build();
        when(licenseRepo.findById(licenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.updateLicense(licenseId, request, "admin"))
                .isInstanceOf(LicenseNotFoundException.class);
    }

    @Test
    void updateLicense_partialUpdate_doesNotOverrideNullFields() {
        UpdateLicenseRequest request = UpdateLicenseRequest.builder()
                .epsQuota(5000)
                .build();

        when(licenseRepo.findById(licenseId)).thenReturn(Optional.of(starterLicense));
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> inv.getArgument(0));

        LicenseResponse response = licenseService.updateLicense(licenseId, request, "admin");

        assertThat(response.getEpsQuota()).isEqualTo(5000);
        assertThat(response.getMode()).isEqualTo(LicenseMode.THROTTLE); // unchanged
        assertThat(response.getPlan()).isEqualTo(LicensePlan.STARTER); // unchanged
    }

    // ── revokeLicense ──

    @Test
    void revokeLicense_success() {
        when(licenseRepo.findById(licenseId)).thenReturn(Optional.of(starterLicense));
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> inv.getArgument(0));

        LicenseResponse response = licenseService.revokeLicense(licenseId, "admin");

        assertThat(response.getStatus()).isEqualTo(LicenseStatus.REVOKED);
        verify(auditLogRepo).save(any(LicenseAuditLog.class));
    }

    @Test
    void revokeLicense_throwsWhenNotFound() {
        when(licenseRepo.findById(licenseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.revokeLicense(licenseId, "admin"))
                .isInstanceOf(LicenseNotFoundException.class);
    }

    // ── getExpiringLicenses ──

    @Test
    void getExpiringLicenses_returnsMatchingLicenses() {
        when(licenseRepo.findLicensesExpiringSoon(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(starterLicense));

        List<LicenseResponse> responses = licenseService.getExpiringLicenses(7);

        assertThat(responses).hasSize(1);
    }

    @Test
    void getExpiringLicenses_emptyWhenNoneExpiring() {
        when(licenseRepo.findLicensesExpiringSoon(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        List<LicenseResponse> responses = licenseService.getExpiringLicenses(7);

        assertThat(responses).isEmpty();
    }

    // ── getAllActivePolicies ──

    @Test
    void getAllActivePolicies_returnsOnlyActive() {
        License revoked = License.builder()
                .tenantId(UUID.randomUUID())
                .epsQuota(100)
                .mode(LicenseMode.THROTTLE)
                .burstMultiplier(1.0)
                .plan(LicensePlan.STARTER)
                .startDate(startDate)
                .endDate(now.minus(10, ChronoUnit.DAYS))
                .status(LicenseStatus.REVOKED)
                .build();

        when(licenseRepo.findAll()).thenReturn(List.of(starterLicense, revoked));

        var policies = licenseService.getAllActivePolicies();

        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).getTenantId()).isEqualTo(tenantId);
    }

    // ── Audit log verification ──

    @Test
    void createLicense_createsAuditLogWithCorrectAction() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.STARTER)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(licenseId);
            return l;
        });
        when(auditLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        licenseService.createLicense(request, "admin_user");

        ArgumentCaptor<LicenseAuditLog> captor = ArgumentCaptor.forClass(LicenseAuditLog.class);
        verify(auditLogRepo).save(captor.capture());

        LicenseAuditLog auditLog = captor.getValue();
        assertThat(auditLog.getAction()).isEqualTo("CREATED");
        assertThat(auditLog.getPerformedBy()).isEqualTo("admin_user");
        assertThat(auditLog.getLicenseId()).isEqualTo(licenseId);
        assertThat(auditLog.getTenantId()).isEqualTo(tenantId);
    }

    // ── plan resolves correctly ──

    @Test
    void createLicense_planResolvesToCorrectValues() {
        CreateLicenseRequest request = CreateLicenseRequest.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.ENTERPRISE)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        when(licenseRepo.findByTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(licenseRepo.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(licenseId);
            return l;
        });

        LicenseResponse response = licenseService.createLicense(request, "admin");

        // Enterprise plan: 2000 EPS, OVERFLOW_BILLING mode, burst 2.0
        assertThat(response.getEpsQuota()).isEqualTo(2000);
        assertThat(response.getMode()).isEqualTo(LicenseMode.OVERFLOW_BILLING);
        assertThat(response.getBurstMultiplier()).isEqualTo(2.0);
        assertThat(response.getPlan()).isEqualTo(LicensePlan.ENTERPRISE);
    }
}