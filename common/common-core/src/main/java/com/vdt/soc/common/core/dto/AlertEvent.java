package com.vdt.soc.common.core.dto;

import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    private UUID tenantId;

    private AlertType alertType;

    private AlertSeverity severity;

    private Double currentValue;

    private Double threshold;

    private String message;

    @Builder.Default
    private Instant occurredAt = Instant.now();
}
