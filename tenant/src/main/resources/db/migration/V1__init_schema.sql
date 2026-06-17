CREATE TABLE tenants (
                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name            VARCHAR(255) NOT NULL,
                         email           VARCHAR(255) NOT NULL UNIQUE,
                         company         VARCHAR(255),
                         phone           VARCHAR(50),
                         status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
                         created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       username        VARCHAR(100) NOT NULL UNIQUE,
                       password_hash   VARCHAR(255) NOT NULL,
                       email           VARCHAR(255) NOT NULL UNIQUE,
                       full_name       VARCHAR(255),
                       tenant_id       UUID REFERENCES tenants(id) ON DELETE SET NULL,  -- NULL cho system admin
                       role            VARCHAR(20) NOT NULL DEFAULT 'TENANT_ADMIN'
                           CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'TENANT_VIEWER')),
                       status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
                       created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_username ON users(username);

CREATE TABLE tenant_api_keys (
                                 id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 tenant_id       UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
                                 api_key_hash    VARCHAR(255) NOT NULL,
                                 status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                     CHECK (status IN ('ACTIVE', 'REVOKED')),
                                 created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_hash ON tenant_api_keys(api_key_hash);
CREATE INDEX idx_api_keys_tenant ON tenant_api_keys(tenant_id);