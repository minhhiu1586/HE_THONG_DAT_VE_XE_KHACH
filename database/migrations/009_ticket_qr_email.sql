-- Persist QR attachments with the same receipt outbox transaction.
ALTER TABLE email_outbox ADD COLUMN attachments_json text NOT NULL DEFAULT '[]';
