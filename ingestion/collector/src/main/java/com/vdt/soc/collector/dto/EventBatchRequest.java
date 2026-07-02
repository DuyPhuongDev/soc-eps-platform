package com.vdt.soc.collector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventBatchRequest {

    public static final int MAX_BATCH_SIZE = 1000;


    @NotEmpty(message = "events must not be empty")
    @Size(min = 1, max = MAX_BATCH_SIZE, message = "batch size must be between 1 and " + MAX_BATCH_SIZE)
    private List<@Valid EventRequest> events;
}
