-- V1__init_schema.sql
-- Notification service: alerts table on TimescaleDB
-- Consumes alert events from Kafka topic 'alert-events'.

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE notify_db.alerts
(
    id         BIGSERIAL,
    tenant_id  UUID        NOT NULL,
    license_id UUID,
    type       VARCHAR(50) NOT NULL
        CHECK (type IN ('USAGE_PERCENT', 'LICENSE_EXPIRING', 'NO_LOG_DETECTED',
                        'EPS_70_PCT', 'EPS_100_PCT', 'MONTHLY_QUOTA_100_PCT')),
    severity   VARCHAR(50) NOT NULL DEFAULT 'INFO',
    threshold  INTEGER,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
);

-- Convert to hypertable, partitioned by created_at (1 day chunks)
SELECT create_hypertable('notify_db.alerts', 'created_at',
    chunk_time_interval => INTERVAL '1 day');

CREATE INDEX idx_alerts_tenant ON notify_db.alerts (tenant_id, created_at DESC);
CREATE INDEX idx_alerts_type ON notify_db.alerts (type, created_at DESC);
CREATE INDEX idx_alerts_unread ON notify_db.alerts (tenant_id, is_read)
    WHERE is_read = FALSE;
