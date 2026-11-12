-- Record which administrator rejected an enrollment without changing
-- existing enrollment rows or their current states.
SET @has_rejected_by := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND column_name = 'rejected_by'
);
SET @ddl := IF(@has_rejected_by = 0,
    'ALTER TABLE enrollments ADD COLUMN rejected_by BIGINT NULL AFTER rejection_reason',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rejected_by_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'enrollments' AND index_name = 'idx_enrollments_rejected_by'
);
SET @ddl := IF(@has_rejected_by_index = 0,
    'ALTER TABLE enrollments ADD INDEX idx_enrollments_rejected_by (rejected_by)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rejected_by_fk := (
    SELECT COUNT(*) FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE() AND table_name = 'enrollments'
      AND column_name = 'rejected_by' AND referenced_table_name = 'users'
);
SET @ddl := IF(@has_rejected_by_fk = 0,
    'ALTER TABLE enrollments ADD CONSTRAINT fk_enrollments_rejected_by FOREIGN KEY (rejected_by) REFERENCES users (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
