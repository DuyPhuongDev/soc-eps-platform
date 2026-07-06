//package com.vdt.soc.notification.service;
//
//import com.vdt.soc.common.core.dto.AlertEvent;
//import com.vdt.soc.notification.entity.Alert;
//import com.vdt.soc.notification.mail.AlertMailer;
//import com.vdt.soc.notification.repository.AlertRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
///**
// * Persists alert events from Kafka.
// * <p>
// * Debounce is a safety net: if AlertJob restarts and loses its in-memory
// * state, we suppress duplicate fires by checking for an existing active
// * alert of the same tenant + type.
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class AlertEngine {
//
//    private final AlertRepository alertRepository;
//    private final AlertMailer alertMailer;
//
//    @Transactional
//    public void process(AlertEvent event) {
//        List<Alert> active = alertRepository.findByTenantIdAndTypeAndIsReadFalse(
//                event.getTenantId(), event.getAlertType());
//        if (!active.isEmpty()) {
//            log.debug("Alert suppressed (already active): tenant={}, type={}",
//                    event.getTenantId(), event.getAlertType());
//            return;
//        }
//        Alert alert = Alert.builder()
//                .tenantId(event.getTenantId())
//                .type(event.getAlertType())
//                .severity(event.getSeverity())
//                .message(event.getMessage())
//                .currentValue(event.getCurrentValue())
//                .threshold(event.getThreshold())
//                .isRead(false)
//                .build();
//        alertRepository.save(alert);
//        alertMailer.send(alert);
//        log.info("Alert persisted: tenant={}, type={}, severity={}",
//                event.getTenantId(), event.getAlertType(), event.getSeverity());
//    }
//}
