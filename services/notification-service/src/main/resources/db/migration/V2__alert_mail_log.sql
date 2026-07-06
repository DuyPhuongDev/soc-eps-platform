-- Delivery log for alert emails. One row per send attempt (SENT or FAILED).
-- alert_id alone is not unique to an alert row because alerts has a composite
-- PK (id, created_at); we keep alert_id + sent_at as a correlation reference,
-- not a foreign key, so a mail-log row survives even if the source alert is
-- later archived/removed.
CREATE TABLE notify_db.alert_mail_log
(
    id        BIGSERIAL PRIMARY KEY,
    alert_id  BIGINT      NOT NULL,
    recipient VARCHAR(255) NULL,
    status    VARCHAR(20) NOT NULL,
    error     TEXT NULL,
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_mail_log_alert ON notify_db.alert_mail_log (alert_id);
CREATE INDEX idx_alert_mail_log_status ON notify_db.alert_mail_log (status);
