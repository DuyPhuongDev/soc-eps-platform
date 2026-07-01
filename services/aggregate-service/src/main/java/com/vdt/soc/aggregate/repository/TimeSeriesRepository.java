package com.vdt.soc.aggregate.repository;

import com.vdt.soc.aggregate.entity.TimeSeriesData;
import com.vdt.soc.aggregate.entity.TimeSeriesDataId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSeriesRepository extends JpaRepository<TimeSeriesData, TimeSeriesDataId> {

    List<TimeSeriesData> findByTenantIdAndBucketTimeBetweenOrderByBucketTimeAsc(
            UUID tenantId, Instant from, Instant to);

    Optional<TimeSeriesData> findByTenantIdAndBucketTime(UUID tenantId, Instant bucketTime);
}
