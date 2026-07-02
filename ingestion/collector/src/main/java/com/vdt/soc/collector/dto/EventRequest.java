package com.vdt.soc.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {


    @NotBlank(message = "eventType is required")
    private String eventType;


    @NotBlank(message = "timestamp is required")
    private String timestamp;

    @NotNull(message = "data is required")
    private Map<String, Object> data;
}
