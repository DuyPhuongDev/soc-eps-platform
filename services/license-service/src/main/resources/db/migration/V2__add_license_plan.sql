-- Add plan column to licenses table
ALTER TABLE license_db.licenses ADD COLUMN plan VARCHAR(30);

-- Backfill existing rows with plan inferred from eps_quota + mode
UPDATE license_db.licenses SET plan = 'STARTER' WHERE eps_quota = 100 AND mode = 'THROTTLE';
UPDATE license_db.licenses SET plan = 'PROFESSIONAL' WHERE eps_quota = 500 AND mode = 'BURST_THEN_THROTTLE';
UPDATE license_db.licenses SET plan = 'ENTERPRISE' WHERE eps_quota = 2000 AND mode = 'OVERFLOW_BILLING';

-- Make plan NOT NULL after backfill
ALTER TABLE license_db.licenses ALTER COLUMN plan SET NOT NULL;
ALTER TABLE license_db.licenses ALTER COLUMN plan SET DEFAULT 'STARTER';

-- Add index for plan lookups
CREATE INDEX idx_licenses_plan ON license_db.licenses(plan);