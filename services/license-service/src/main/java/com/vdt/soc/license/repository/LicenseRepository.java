package com.vdt.soc.license.repository;

import com.vdt.soc.common.core.enumeration.LicenseStatus;
import com.vdt.soc.license.entity.License;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenseRepository extends JpaRepository<License, UUID> {

    Optional<License> findByTenantIdAndStatus(UUID tenantId, LicenseStatus status);

    Page<License> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT l FROM License l WHERE l.status = 'ACTIVE' AND l.endDate BETWEEN :now AND :futureDate")
    List<License> findLicensesExpiringSoon(@Param("now") Instant now, @Param("futureDate") Instant futureDate);
}