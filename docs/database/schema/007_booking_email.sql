ALTER TABLE don_dat_ve ADD COLUMN IF NOT EXISTS khach_dat_email varchar(254);
CREATE TABLE IF NOT EXISTS email_outbox (
    ma_don varchar(40) PRIMARY KEY REFERENCES don_dat_ve(ma_don) ON DELETE CASCADE,
    recipient varchar(254) NOT NULL,
    recipient_name varchar(160) NOT NULL,
    subject varchar(255) NOT NULL,
    html_content text NOT NULL,
    text_content text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL,
    created_at timestamp NOT NULL,
    started_at timestamp,
    sent_at timestamp,
    provider_message_id varchar(255),
    last_error varchar(120),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','UNKNOWN'))
);
CREATE INDEX IF NOT EXISTS ix_email_outbox_pending ON email_outbox(status,next_attempt_at);
