-- Add monthly event volume quota
-- monthly_quota: max events per 30-day window from license start date
ALTER TABLE license_db.licenses
    ADD COLUMN IF NOT EXISTS monthly_quota BIGINT NOT NULL DEFAULT 3000000
    CHECK (monthly_quota > 0);
