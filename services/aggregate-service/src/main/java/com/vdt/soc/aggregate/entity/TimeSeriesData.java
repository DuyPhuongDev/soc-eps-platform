package com.vdt.soc.aggregate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to {@code timeseries_data} table in aggregate_db schema (TimescaleDB hypertable).
 * One row per tenant per minute bucket.
 * <p>
 * Primary key is composite: {@code (tenant_id, bucket_min)}.
 */
@Entity
@Table(name = "timeseries_data", schema = "aggregate_db")
@IdClass(TimeSeriesDataId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSeriesData {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "bucket_min", nullable = false)
    private Instant bucketMin;

    @Column(nullable = false)
    @Builder.Default
    private long accepted = 0L;

    @Column(nullable = false)
    @Builder.Default
    private long dropped = 0L;

    @Column(name = "max_eps")
    private Long maxEps;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}