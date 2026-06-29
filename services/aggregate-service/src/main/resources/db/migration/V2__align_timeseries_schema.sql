-- V2__align_timeseries_schema.sql
-- Align timeseries_data schema: remove surrogate id, use (tenant_id, bucket_min) as PK, add max_eps

-- Drop hypertable to allow schema changes (keeps underlying table and data)
SELECT drop_hypertable('aggregate_db.timeseries_data', cascade_to_materializations => FALSE);

-- Drop old primary key constraint (id, bucket_min)
ALTER TABLE aggregate_db.timeseries_data DROP CONSTRAINT IF EXISTS timeseries_data_pkey;

-- Drop old unique constraint (tenant_id, bucket_min) — will be replaced by new PK
ALTER TABLE aggregate_db.timeseries_data DROP CONSTRAINT IF EXISTS timeseries_data_tenant_id_bucket_min_key;

-- Drop the surrogate id column
ALTER TABLE aggregate_db.timeseries_data DROP COLUMN IF EXISTS id;

-- Add max_eps column (BIGINT, nullable — records peak EPS within the minute bucket)
ALTER TABLE aggregate_db.timeseries_data ADD COLUMN IF NOT EXISTS max_eps BIGINT;

-- Set composite primary key (tenant_id, bucket_min)
ALTER TABLE aggregate_db.timeseries_data ADD PRIMARY KEY (tenant_id, bucket_min);

-- Re-create hypertable, partitioned by bucket_min (1 day chunks)
SELECT create_hypertable('aggregate_db.timeseries_data', 'bucket_min',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE);
