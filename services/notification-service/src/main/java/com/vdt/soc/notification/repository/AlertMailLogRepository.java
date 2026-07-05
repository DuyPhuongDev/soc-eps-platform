package com.vdt.soc.notification.repository;

import com.vdt.soc.notification.entity.AlertMailLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for alert-mail delivery log.
 */
public interface AlertMailLogRepository extends JpaRepository<AlertMailLog, Long> {
}
