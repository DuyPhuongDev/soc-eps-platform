-- Bảng licenses
CREATE TABLE licenses
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_by       varchar(255),
    last_modified_by varchar(255),
    deleted          BOOLEAN              DEFAULT false,
    tenant_id        UUID        NOT NULL, -- FK logic đến tenant-service.tenants
    eps_quota        INTEGER     NOT NULL CHECK (eps_quota > 0),
    mode             VARCHAR(50) NOT NULL DEFAULT 'THROTTLE'
        CHECK (mode IN ('THROTTLE', 'BURST_THEN_THROTTLE', 'OVERFLOW_BILLING')),
    burst_multiplier DOUBLE PRECISION     DEFAULT 1.0,
    start_date       TIMESTAMPTZ NOT NULL,
    end_date         TIMESTAMPTZ NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dates CHECK (end_date > start_date)
);

-- Bảng audit log
CREATE TABLE license_audit_logs
(
    id           BIGSERIAL PRIMARY KEY,
    license_id   UUID        NOT NULL,
    tenant_id    UUID        NOT NULL,
    action       VARCHAR(50) NOT NULL, -- CREATED, UPDATED, REVOKED, EXPIRED
    changes      JSONB,                -- old_value, new_value
    performed_by VARCHAR(255),         -- username của admin
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Bảng alerts
CREATE TABLE alerts
(
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  UUID        NOT NULL,
    license_id UUID        REFERENCES licenses (id) ON DELETE SET NULL,
    type       VARCHAR(50) NOT NULL
        CHECK (type IN ('USAGE_PERCENT', 'LICENSE_EXPIRING', 'NO_LOG_DETECTED')),
    threshold  INTEGER,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_licenses_tenant_id ON licenses (tenant_id);
CREATE INDEX idx_licenses_status ON licenses (status);
CREATE INDEX idx_licenses_end_date ON licenses (end_date);
CREATE INDEX idx_licenses_tenant_status ON licenses (tenant_id, status);
CREATE UNIQUE INDEX idx_one_active_license_per_tenant
    ON licenses (tenant_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_audit_logs_license ON license_audit_logs (license_id);
CREATE INDEX idx_audit_logs_tenant ON license_audit_logs (tenant_id);
CREATE INDEX idx_audit_logs_created ON license_audit_logs (created_at);

CREATE INDEX idx_alerts_tenant_id ON alerts (tenant_id);
CREATE INDEX idx_alerts_type ON alerts (type);
CREATE INDEX idx_alerts_created ON alerts (created_at);
CREATE UNIQUE INDEX idx_one_alert_per_tenant ON alerts (tenant_id, license_id, type, threshold);