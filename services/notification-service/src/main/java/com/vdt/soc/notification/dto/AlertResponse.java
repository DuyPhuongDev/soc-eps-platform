package com.vdt.soc.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertResponse {

    private Long id;
    private UUID tenantId;
    private AlertType type;
    private AlertSeverity severity;
    private Double threshold;
    private String message;
    private boolean isRead;
    private Instant createdAt;
}
