CREATE TABLE notify_db.alerts
(
    id            BIGSERIAL,
    tenant_id     UUID        NOT NULL,
    type          VARCHAR(50) NOT NULL
        CHECK (type IN ('USAGE_PERCENT', 'LICENSE_EXPIRING', 'NO_LOG_DETECTED',
                        'EPS_70_PCT', 'EPS_100_PCT', 'MONTHLY_QUOTA_100_PCT')),
    severity      VARCHAR(50) NOT NULL DEFAULT 'INFO',
    current_value DOUBLE PRECISION,
    threshold     DOUBLE PRECISION,
    message       TEXT        NOT NULL,
    is_read       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
);

CREATE INDEX idx_alerts_tenant ON notify_db.alerts (tenant_id, created_at DESC);
CREATE INDEX idx_alerts_type ON notify_db.alerts (type, created_at DESC);
CREATE INDEX idx_alerts_unread ON notify_db.alerts (tenant_id, is_read)
    WHERE is_read = FALSE;
