package com.vdt.soc.notification.engine;

import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles alert debounce, persistence, and resolution.
 * <p>
 * Consumed by {@link com.vdt.soc.notification.consumer.AlertEventConsumer}
 * which receives events from Kafka topic {@code alert-events}.
 * <p>
 * Debounce rules:
 * <ul>
 *   <li>{@code EPS_100_PCT}: fire once when dropped events appear.
 *       Re-fire only after previous alert was resolved.</li>
 *   <li>{@code EPS_70_PCT}: fire once when EPS >= 70% of quota.
 *       Re-fire only after EPS drops below 70% then crosses again.</li>
 *   <li>{@code MONTHLY_QUOTA_100_PCT}: fire once per billing window.</li>
 *   <li>{@code LICENSE_EXPIRING}: fire once per license (N days before expiry).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEngine {

    private final AlertRepository alertRepository;

    /**
     * Process an alert event from Kafka.
     *
     * @param tenantId  tenant ID
     * @param licenseId optional license ID (may be null for EPS/quota alerts)
     * @param type      alert type (e.g. "EPS_70_PCT", "LICENSE_EXPIRING")
     * @param severity  "WARNING", "CRITICAL", "INFO"
     * @param message   human-readable alert message
     * @param threshold optional threshold value
     * @param resolved  true = resolve alert, false = fire alert
     */
    @Transactional
    public void process(UUID tenantId, UUID licenseId, String type, String severity,
                        String message, Integer threshold, boolean resolved) {
        if (resolved) {
            resolve(tenantId, type);
        } else {
            fireIfNotActive(tenantId, licenseId, type, severity, message, threshold);
        }
    }

    /**
     * Fire an alert only if no active (unread) alert of the same type exists for this tenant.
     */
    private void fireIfNotActive(UUID tenantId, UUID licenseId, String type,
                                 String severity, String message, Integer threshold) {
        List<Alert> active = alertRepository.findByTenantIdAndTypeAndIsReadFalse(tenantId, type);
        if (!active.isEmpty()) {
            log.debug("Alert suppressed (already active): tenant={}, type={}", tenantId, type);
            return;
        }
        Alert alert = Alert.builder()
                .tenantId(tenantId)
                .licenseId(licenseId)
                .type(type)
                .severity(severity)
                .message(message)
                .threshold(threshold)
                .isRead(false)
                .build();
        alertRepository.save(alert);
        log.info("Alert persisted: tenant={}, type={}, severity={}, message={}",
                tenantId, type, severity, message);
    }

    /**
     * Mark all active alerts of a given type for a tenant as read.
     */
    private void resolve(UUID tenantId, String type) {
        int updated = alertRepository.markAsReadByTenantIdAndType(tenantId, type);
        if (updated > 0) {
            log.info("Alert resolved: tenant={}, type={}, count={}", tenantId, type, updated);
        }
    }
}
