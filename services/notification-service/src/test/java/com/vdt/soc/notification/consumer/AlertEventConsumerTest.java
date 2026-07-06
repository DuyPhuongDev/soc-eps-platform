package com.vdt.soc.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.AlertEvent;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.notification.engine.AlertEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEventConsumerTest {

    @Mock
    private AlertEngine alertEngine;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AlertEventConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void onAlertEvent_deserializesAndProcesses() throws Exception {
        AlertEvent event = AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(AlertType.MONTHLY_QUOTA_100_PCT)
                .severity(AlertSeverity.WARNING)
                .build();
        when(objectMapper.readValue(anyString(), eq(AlertEvent.class))).thenReturn(event);

        consumer.onAlertEvent("{\"tenantId\":\"" + tenantId + "\"}");

        verify(alertEngine).process(event);
    }

    @Test
    void onAlertEvent_handlesJsonError() throws Exception {
        when(objectMapper.readValue(anyString(), eq(AlertEvent.class)))
                .thenThrow(new JsonProcessingException("bad json") {});

        consumer.onAlertEvent("bad json");

        verify(alertEngine, never()).process(any());
    }

    @Test
    void onAlertEvent_handlesRuntimeException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(AlertEvent.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onAlertEvent("{}");

        verify(alertEngine, never()).process(any());
    }
}
