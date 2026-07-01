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
    @Column(name = "bucket_time", nullable = false)
    private Instant bucketTime;

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
