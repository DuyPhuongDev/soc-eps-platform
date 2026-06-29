package com.vdt.soc.aggregate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Grafana Simple JSON Datasource timeserie response format.
 * Response is a List of SeriesItem:
 * <pre>
 * [
 *   {
 *     "target": "accepted_eps",
 *     "datapoints": [[85.5, 1747936800000], [92.3, 1747936860000]]
 *   }
 * ]
 * </pre>
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeseriesResponse {

    private String target;
    /**
     * Array of [value: float|null, timestamp_ms: long].
     * null value = no data point → Grafana shows line break (gap).
     */
    private List<List<Number>> datapoints;
}