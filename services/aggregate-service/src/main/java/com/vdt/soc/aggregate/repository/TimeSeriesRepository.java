package com.vdt.soc.aggregate.repository;

import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.entity.TimeSeriesDataId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSeriesRepository extends JpaRepository<TimeSeriesData, TimeSeriesDataId> {

    /**
     * Query time-series data for a tenant within a time range,
     * ordered by bucket_min ascending (for building chart datapoints).
     */
    List<TimeSeriesData> findByTenantIdAndBucketMinBetweenOrderByBucketMinAsc(
            UUID tenantId, Instant from, Instant to);

    /**
     * Find a specific bucket row for upsert.
     */
    Optional<TimeSeriesData> findByTenantIdAndBucketMin(UUID tenantId, Instant bucketMin);
}