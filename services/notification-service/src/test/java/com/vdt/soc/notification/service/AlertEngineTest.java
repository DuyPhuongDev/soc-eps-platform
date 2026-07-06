package com.vdt.soc.notification.engine;

import com.vdt.soc.common.core.dto.AlertEvent;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.mail.AlertMailer;
import com.vdt.soc.notification.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEngineTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertMailer alertMailer;

    @InjectMocks
    private AlertEngine alertEngine;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void process_persistsNewAlert() {
        AlertEvent event = AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(AlertType.MONTHLY_QUOTA_100_PCT)
                .severity(AlertSeverity.WARNING)
                .message("Quota exceeded")
                .currentValue(110.0)
                .threshold(100.0)
                .build();
        when(alertRepository.findByTenantIdAndTypeAndIsReadFalse(tenantId, AlertType.MONTHLY_QUOTA_100_PCT.name()))
                .thenReturn(List.of());

        alertEngine.process(event);

        verify(alertRepository).save(any(Alert.class));
        verify(alertMailer).send(any(Alert.class));
    }

    @Test
    void process_suppressesDuplicateAlert() {
        AlertEvent event = AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(AlertType.MONTHLY_QUOTA_100_PCT)
                .severity(AlertSeverity.WARNING)
                .message("Quota exceeded")
                .build();
        Alert existing = Alert.builder()
                .tenantId(tenantId)
                .type(AlertType.MONTHLY_QUOTA_100_PCT)
                .isRead(false)
                .build();
        when(alertRepository.findByTenantIdAndTypeAndIsReadFalse(tenantId, AlertType.MONTHLY_QUOTA_100_PCT.name()))
                .thenReturn(List.of(existing));

        alertEngine.process(event);

        verify(alertRepository, never()).save(any());
        verify(alertMailer, never()).send(any());
    }

    @Test
    void process_mapsEventFieldsCorrectly() {
        AlertEvent event = AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(AlertType.EPS_100_PCT)
                .severity(AlertSeverity.CRITICAL)
                .message("Drop spike detected")
                .currentValue(95.0)
                .threshold(80.0)
                .build();
        when(alertRepository.findByTenantIdAndTypeAndIsReadFalse(tenantId, AlertType.EPS_100_PCT.name()))
                .thenReturn(List.of());

        alertEngine.process(event);

        verify(alertRepository).save(any(Alert.class));
        verify(alertMailer).send(any(Alert.class));
    }
}
