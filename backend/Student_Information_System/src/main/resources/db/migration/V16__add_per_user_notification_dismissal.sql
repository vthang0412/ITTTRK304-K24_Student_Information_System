ALTER TABLE notification_reads
    ADD COLUMN is_dismissed BOOLEAN NOT NULL DEFAULT FALSE;
