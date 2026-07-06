-- V1__init_schema.sql

CREATE
EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE aggregate_db.timeseries_data
(
    tenant_id   UUID        NOT NULL,
    bucket_time TIMESTAMPTZ NOT NULL,
    accepted    BIGINT      NOT NULL DEFAULT 0,
    dropped     BIGINT      NOT NULL DEFAULT 0,
    max_eps     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, bucket_time)
);

SELECT create_hypertable('aggregate_db.timeseries_data', 'bucket_time',
                         chunk_time_interval = > INTERVAL '1 day',
                         if_not_exists = > TRUE);

CREATE INDEX IF NOT EXISTS idx_ts_tenant_bucket
    ON aggregate_db.timeseries_data (tenant_id, bucket_time);
