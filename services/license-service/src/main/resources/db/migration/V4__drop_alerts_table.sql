-- V4__drop_alerts_table.sql
-- Alerts are now handled by notification-service via Kafka events.
-- Drop the old alerts table from license_db.

DROP TABLE IF EXISTS license_db.alerts CASCADE;
