package com.vdt.soc.notification.controller;

import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.notification.dto.AlertResponse;
import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertController alertController;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void listAlerts_byTenantIdAndActiveStatus() {
        Alert alert = Alert.builder()
                .tenantId(tenantId)
                .type(AlertType.MONTHLY_QUOTA_100_PCT)
                .severity(AlertSeverity.WARNING)
                .isRead(false)
                .build();
        when(alertRepository.findByTenantIdAndIsReadFalse(tenantId)).thenReturn(List.of(alert));

        ResponseEntity<List<AlertResponse>> response = alertController.listAlerts(tenantId, "ACTIVE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isRead()).isFalse();
    }

    @Test
    void listAlerts_allStatus() {
        Alert alert = Alert.builder()
                .tenantId(tenantId)
                .type(AlertType.MONTHLY_QUOTA_100_PCT)
                .severity(AlertSeverity.WARNING)
                .isRead(true)
                .build();
        when(alertRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(alert));

        ResponseEntity<List<AlertResponse>> response = alertController.listAlerts(tenantId, "ALL");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isRead()).isTrue();
    }

    @Test
    void listAlerts_withoutTenantId_returnsAll() {
        Alert alert = Alert.builder()
                .tenantId(tenantId)
                .type(AlertType.MONTHLY_QUOTA_100_PCT)
                .isRead(false)
                .build();
        when(alertRepository.findAll()).thenReturn(List.of(alert));

        ResponseEntity<List<AlertResponse>> response = alertController.listAlerts(null, "ALL");

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listAlerts_withoutTenantIdAndActiveStatus() {
        Alert alert1 = Alert.builder().tenantId(tenantId).type(AlertType.MONTHLY_QUOTA_100_PCT).isRead(false).build();
        Alert alert2 = Alert.builder().tenantId(tenantId).type(AlertType.EPS_100_PCT).isRead(true).build();
        when(alertRepository.findAll()).thenReturn(List.of(alert1, alert2));

        ResponseEntity<List<AlertResponse>> response = alertController.listAlerts(null, "ACTIVE");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isRead()).isFalse();
    }
}
