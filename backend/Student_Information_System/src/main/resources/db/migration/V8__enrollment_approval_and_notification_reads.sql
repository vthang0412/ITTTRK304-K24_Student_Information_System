-- ============================================================
-- V8: Enrollment approval workflow + per-user notification read state.
--
-- Changes
-- -------
-- 1) enrollments.status: add PENDING (waiting for approval) and REJECTED.
--    ENROLLED remains the official "approved" status so existing queries
--    and data keep working; WITHDRAWN is unchanged.
-- 2) enrollments: add approved_at / approved_by / rejected_at /
--    rejection_reason columns used by the approval workflow.
-- 3) notification_reads: per-user read state so that a broadcast
--    notification can be read by one user without hiding it from others.
--    The legacy notifications.is_read column is kept for backward
--    compatibility but is no longer used to compute per-user read state.
--
-- Conditional DDL allows reconciliation when Hibernate created some or all
-- approval structures before Flyway was enabled. No data is deleted.
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 1) Extend enrollment status enum.
-- ------------------------------------------------------------
ALTER TABLE enrollments
    MODIFY COLUMN status ENUM('PENDING','ENROLLED','REJECTED','WITHDRAWN')
    COLLATE utf8mb4_unicode_ci NOT NULL;

-- ------------------------------------------------------------
-- 2) Approval workflow columns.
-- ------------------------------------------------------------
SET @has_approved_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND column_name = 'approved_at'
);
SET @ddl := IF(@has_approved_at = 0,
    'ALTER TABLE enrollments ADD COLUMN approved_at DATETIME(6) NULL AFTER withdrawn_at',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approved_by := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND column_name = 'approved_by'
);
SET @ddl := IF(@has_approved_by = 0,
    'ALTER TABLE enrollments ADD COLUMN approved_by BIGINT NULL AFTER approved_at',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rejected_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND column_name = 'rejected_at'
);
SET @ddl := IF(@has_rejected_at = 0,
    'ALTER TABLE enrollments ADD COLUMN rejected_at DATETIME(6) NULL AFTER approved_by',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rejection_reason := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND column_name = 'rejection_reason'
);
SET @ddl := IF(@has_rejection_reason = 0,
    'ALTER TABLE enrollments ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER rejected_at',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_status_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND index_name = 'idx_enrollments_status'
);
SET @ddl := IF(@has_status_index = 0,
    'ALTER TABLE enrollments ADD INDEX idx_enrollments_status (status)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approved_by_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND index_name = 'idx_enrollments_approved_by'
);
SET @ddl := IF(@has_approved_by_index = 0,
    'ALTER TABLE enrollments ADD INDEX idx_enrollments_approved_by (approved_by)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approved_by_fk := (
    SELECT COUNT(*) FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE() AND table_name = 'enrollments'
      AND column_name = 'approved_by' AND referenced_table_name = 'users'
);
SET @ddl := IF(@has_approved_by_fk = 0,
    'ALTER TABLE enrollments ADD CONSTRAINT fk_enrollments_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3) Per-user notification read state.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_reads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_reads_notification_user (notification_id, user_id),
    INDEX idx_notification_reads_user (user_id),
    CONSTRAINT fk_notification_reads_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_reads_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
