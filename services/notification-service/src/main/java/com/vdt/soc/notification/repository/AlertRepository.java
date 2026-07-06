package com.vdt.soc.notification.repository;

import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.notification.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * Find active (unread) alerts of a given type for a tenant.
     * Used for debounce: don't re-fire if alert already active.
     */
    List<Alert> findByTenantIdAndTypeAndIsReadFalse(UUID tenantId, AlertType type);

    /**
     * Find all unread alerts for a tenant.
     */
    List<Alert> findByTenantIdAndIsReadFalse(UUID tenantId);

    /**
     * Find all alerts for a tenant, ordered by creation time descending.
     */
    List<Alert> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Find alerts for a tenant within a time range.
     */
    List<Alert> findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID tenantId, Instant from, Instant to);

    /**
     * Mark all active alerts of a given type for a tenant as read.
     * Returns count of updated rows.
     */
    @Modifying
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.tenantId = :tenantId AND a.type = :type AND a.isRead = false")
    int markAsReadByTenantIdAndType(@Param("tenantId") UUID tenantId, @Param("type") String type);
}
