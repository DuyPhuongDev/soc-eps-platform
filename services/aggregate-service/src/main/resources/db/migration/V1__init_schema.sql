-- V1__init_schema.sql
-- Aggregate service: timeseries_data on TimescaleDB
-- Alerts are now handled by notification-service via Kafka events.

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE aggregate_db.timeseries_data
(
    id         BIGSERIAL,
    tenant_id  UUID        NOT NULL,
    bucket_min TIMESTAMPTZ NOT NULL,
    accepted   BIGINT      NOT NULL DEFAULT 0,
    dropped    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, bucket_min),       -- TimescaleDB requires partition column in PK
    UNIQUE (tenant_id, bucket_min)
);

-- Convert to hypertable, partitioned by bucket_min (1 day chunks)
SELECT create_hypertable('aggregate_db.timeseries_data', 'bucket_min',
    chunk_time_interval => INTERVAL '1 day');

CREATE INDEX idx_ts_tenant_bucket
    ON aggregate_db.timeseries_data (tenant_id, bucket_min);
