package com.vdt.soc.license.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.enumeration.LicenseStatus;
import com.vdt.soc.license.dto.CreateLicenseRequest;
import com.vdt.soc.license.dto.LicenseAuditLogResponse;
import com.vdt.soc.license.dto.LicenseResponse;
import com.vdt.soc.license.dto.PageResponse;
import com.vdt.soc.license.dto.UpdateLicenseRequest;
import com.vdt.soc.license.entity.License;
import com.vdt.soc.license.entity.LicenseAuditLog;
import com.vdt.soc.license.etcd.EtcdPolicyPublisher;
import com.vdt.soc.license.exception.LicenseNotFoundException;
import com.vdt.soc.license.mapper.LicenseMapper;
import com.vdt.soc.license.repository.LicenseAuditLogRepository;
import com.vdt.soc.license.repository.LicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepo;
    private final LicenseAuditLogRepository auditLogRepo;
    private final ObjectMapper objectMapper;
    private final LicenseMapper licenseMapper;
    private final EtcdPolicyPublisher etcdPolicyPublisher;

    @Transactional
    public LicenseResponse createLicense(CreateLicenseRequest request, String performedBy) {
        log.info("Creating new license for tenant {} with plan {}", request.getTenantId(), request.getPlan());
        licenseRepo.findByTenantIdAndStatus(request.getTenantId(), LicenseStatus.ACTIVE)
                .ifPresent(license -> {
                    throw new IllegalStateException("Tenant already has an active license");
                });

        License license = licenseMapper.toEntity(request);
        License savedLicense = licenseRepo.save(license);

        // Publish to etcd for real-time collector sync
        etcdPolicyPublisher.publish(savedLicense);

        // Audit log
        auditLogRepo.save(buildAuditLog(savedLicense, "CREATED", toJson(savedLicense), performedBy));

        log.info("License created successfully: id={}, tenant={}, plan={}", savedLicense.getId(), savedLicense.getTenantId(), savedLicense.getPlan());
        return licenseMapper.toResponse(savedLicense);
    }

    @Transactional(readOnly = true)
    public LicenseResponse getLicense(UUID licenseId) {
        log.debug("Fetching license by id: {}", licenseId);
        License license = findOrThrow(licenseId);
        log.debug("License found: {}", licenseId);
        return licenseMapper.toResponse(license);
    }

    @Transactional(readOnly = true)
    public PageResponse<LicenseResponse> listLicenses(UUID tenantId, Pageable pageable) {
        log.debug("Listing licenses for tenant {} with pageable: {}", tenantId, pageable);
        Page<License> licensePage = licenseRepo.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        log.debug("Found {} licenses for tenant {}", licensePage.getContent().size(), tenantId);
        return licenseMapper.toPageResponse(licensePage);
    }

    @Transactional(readOnly = true)
    public PageResponse<LicenseResponse> listAllLicenses(Pageable pageable) {
        log.debug("Listing all licenses");
        PageResponse<LicenseResponse> licenses = licenseMapper.toPageResponse(licenseRepo.findAll(pageable));
        log.debug("Found {} licenses total", licenses.getContent().size());
        return licenses;
    }

    @Transactional
    public LicenseResponse updateLicense(UUID licenseId, UpdateLicenseRequest request, String performedBy) {
        log.info("Updating license {} by {}", licenseId, performedBy);
        License license = findOrThrow(licenseId);

        String oldValue = toJson(license);

        licenseMapper.updateEntity(request, license);

        license = licenseRepo.save(license);

        // Publish updated policy to etcd
        etcdPolicyPublisher.publish(license);

        // Audit log
        String newValue = toJson(license);
        auditLogRepo.save(buildAuditLog(license, "UPDATED",
                toJson(Map.of("old", oldValue, "new", newValue)),
                performedBy));

        log.info("License {} updated successfully by {}", licenseId, performedBy);
        return licenseMapper.toResponse(license);
    }

    @Transactional
    public LicenseResponse revokeLicense(UUID licenseId, String performedBy) {
        log.info("Revoking license {} by {}", licenseId, performedBy);
        License license = findOrThrow(licenseId);
        license.setStatus(LicenseStatus.REVOKED);
        license = licenseRepo.save(license);

        // Remove policy from etcd — collector will stop allowing this tenant
        etcdPolicyPublisher.remove(license.getTenantId());

        auditLogRepo.save(buildAuditLog(license, "REVOKED", null, performedBy));

        log.info("License {} revoked successfully by {}", licenseId, performedBy);
        return licenseMapper.toResponse(license);
    }

    @Transactional(readOnly = true)
    public List<LicenseResponse> getExpiringLicenses(int days) {
        log.debug("Fetching licenses expiring within {} days", days);
        Instant now = Instant.now();
        Instant futureDate = now.plus(days, ChronoUnit.DAYS);
        List<LicenseResponse> licenses = licenseMapper.toResponseList(licenseRepo.findLicensesExpiringSoon(now, futureDate));
        log.debug("Found {} licenses expiring within {} days", licenses.size(), days);
        return licenses;
    }

    @Transactional(readOnly = true)
    public List<PolicyDTO> getAllActivePolicies() {
        log.debug("Fetching all active policies");
        List<PolicyDTO> policies = licenseRepo.findAll().stream()
                .filter(License::isActive)
                .map(this::toPolicyDTO)
                .toList();
        log.debug("Found {} active policies", policies.size());
        return policies;
    }

    // ── Helper methods ──

    private License findOrThrow(UUID licenseId) {
        log.debug("Looking up license by id: {}", licenseId);
        return licenseRepo.findById(licenseId)
                .orElseThrow(() -> {
                    log.warn("License not found: {}", licenseId);
                    return new LicenseNotFoundException(licenseId);
                });
    }

    private LicenseAuditLog buildAuditLog(License license, String action, String changes, String performedBy) {
        return LicenseAuditLog.builder()
                .licenseId(license.getId())
                .tenantId(license.getTenantId())
                .action(action)
                .changes(changes)
                .performedBy(performedBy)
                .build();
    }

    private PolicyDTO toPolicyDTO(License license) {
        return PolicyDTO.builder()
                .tenantId(license.getTenantId())
                .plan(license.getPlan())
                .epsQuota(license.getEpsQuota())
                .mode(license.getMode())
                .burstMultiplier(license.getBurstMultiplier())
                .validUntil(license.getEndDate())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}