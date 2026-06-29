package com.vdt.soc.aggregate.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Composite primary key for {@link TimeSeriesData}.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TimeSeriesDataId implements Serializable {

    private UUID tenantId;
    private Instant bucketMin;
}
