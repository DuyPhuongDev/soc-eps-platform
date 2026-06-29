package com.vdt.soc.aggregate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private UUID licenseId;
    private String type;
    private String severity;
    private Integer threshold;
    private String message;
    private boolean isRead;
    private Instant createdAt;
}