package com.vdt.soc.common.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SimpleNotificationEvent {
    private String eventId;
    private String sourceService;
    private String eventType;
    private String targetId;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
