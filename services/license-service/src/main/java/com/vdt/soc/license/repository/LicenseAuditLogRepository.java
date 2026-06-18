package com.vdt.soc.license.repository;

import com.vdt.soc.license.entity.LicenseAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LicenseAuditLogRepository extends JpaRepository<LicenseAuditLog, Long> {
    List<LicenseAuditLog> findByLicenseIdOrderByCreatedAtDesc(UUID licenseId);
}