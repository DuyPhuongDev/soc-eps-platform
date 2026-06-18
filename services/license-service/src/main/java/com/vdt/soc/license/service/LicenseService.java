package com.vdt.soc.license.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.license.dto.CreateLicenseRequest;
import com.vdt.soc.license.dto.LicenseAuditLogResponse;
import com.vdt.soc.license.dto.LicenseResponse;
import com.vdt.soc.common.model.dto.PolicyDTO;
import com.vdt.soc.license.dto.UpdateLicenseRequest;
import com.vdt.soc.license.entity.License;
import com.vdt.soc.license.entity.LicenseAuditLog;
import com.vdt.soc.common.model.enumeration.LicensePlan;
import com.vdt.soc.common.model.enumeration.LicenseStatus;
import com.vdt.soc.license.exception.LicenseNotFoundException;
import com.vdt.soc.license.repository.LicenseAuditLogRepository;
import com.vdt.soc.license.repository.LicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepo;
    private final LicenseAuditLogRepository auditLogRepo;
    private final ObjectMapper objectMapper;

    /**
     * Create new license for tenant
     * Just one ACTIVE license exist in one time
     * epsQuota, mode, burstMultiplier was resolved from plan.
     */
    public LicenseResponse createLicense(CreateLicenseRequest request, String performedBy) {
        log.info("Creating new license ");
        licenseRepo.findByTenantIdAndStatus(request.getTenantId(), LicenseStatus.ACTIVE)
                .ifPresent(l -> {
                    throw new IllegalStateException("Tenant already has an active license");
                });

        LicensePlan plan = request.getPlan();

        License license = License.builder()
                .tenantId(request.getTenantId())
                .epsQuota(plan.getEpsQuota())
                .mode(plan.getMode())
                .burstMultiplier(plan.getBurstMultiplier())
                .plan(plan)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(LicenseStatus.ACTIVE)
                .build();
        license = licenseRepo.save(license);

        // Audit log
        auditLogRepo.save(buildAuditLog(license, "CREATED", toJson(license), performedBy));

        return toResponse(license);
    }

    /**
     * Lấy chi tiết license.
     */
    @Transactional(readOnly = true)
    public LicenseResponse getLicense(UUID licenseId) {
        License license = findOrThrow(licenseId);
        return toResponse(license);
    }

    /**
     * Lấy danh sách license theo tenantId.
     */
    @Transactional(readOnly = true)
    public List<LicenseResponse> listLicenses(UUID tenantId) {
        return licenseRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Lấy tất cả license.
     */
    @Transactional(readOnly = true)
    public List<LicenseResponse> listAllLicenses() {
        return licenseRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Cập nhật license (gia hạn, thay đổi quota, mode).
     */
    public LicenseResponse updateLicense(UUID licenseId, UpdateLicenseRequest request, String performedBy) {
        License license = findOrThrow(licenseId);

        String oldValue = toJson(license);

        if (request.getEpsQuota() != null) license.setEpsQuota(request.getEpsQuota());
        if (request.getMode() != null) license.setMode(request.getMode());
        if (request.getBurstMultiplier() != null) license.setBurstMultiplier(request.getBurstMultiplier());
        if (request.getStartDate() != null) license.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) license.setEndDate(request.getEndDate());

        license = licenseRepo.save(license);

        // Audit log
        String newValue = toJson(license);
        auditLogRepo.save(buildAuditLog(license, "UPDATED",
                toJson(java.util.Map.of("old", oldValue, "new", newValue)),
                performedBy));

        return toResponse(license);
    }

    /**
     * Thu hồi license (soft delete – đặt status = REVOKED).
     */
    public LicenseResponse revokeLicense(UUID licenseId, String performedBy) {
        License license = findOrThrow(licenseId);
        license.setStatus(LicenseStatus.REVOKED);
        license = licenseRepo.save(license);

        auditLogRepo.save(buildAuditLog(license, "REVOKED", null, performedBy));

        return toResponse(license);
    }

    /**
     * Lấy danh sách license sắp hết hạn (trong N ngày tới).
     */
    @Transactional(readOnly = true)
    public List<LicenseResponse> getExpiringLicenses(int days) {
        Instant now = Instant.now();
        Instant futureDate = now.plus(days, ChronoUnit.DAYS);
        return licenseRepo.findLicensesExpiringSoon(now, futureDate).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Lấy audit log của một license.
     */
    @Transactional(readOnly = true)
    public List<LicenseAuditLogResponse> getAuditLogs(UUID licenseId) {
        findOrThrow(licenseId); // đảm bảo license tồn tại
        return auditLogRepo.findByLicenseIdOrderByCreatedAtDesc(licenseId).stream()
                .map(LicenseAuditLogResponse::from)
                .toList();
    }

    /**
     * Internal API: Lấy tất cả policy đang active cho Collector.
     */
    @Transactional(readOnly = true)
    public List<PolicyDTO> getAllActivePolicies() {
        return licenseRepo.findAll().stream()
                .filter(License::isActive)
                .map(this::toPolicyDTO)
                .toList();
    }

    // ── Helper methods ──

    private License findOrThrow(UUID licenseId) {
        return licenseRepo.findById(licenseId)
                .orElseThrow(() -> new LicenseNotFoundException(licenseId));
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

    private LicenseResponse toResponse(License license) {
        return LicenseResponse.from(license);
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