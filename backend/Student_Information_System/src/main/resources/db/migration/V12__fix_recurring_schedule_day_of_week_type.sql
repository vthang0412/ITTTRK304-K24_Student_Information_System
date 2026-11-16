SET @has_cancelled_from := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_section_schedules'
      AND column_name = 'cancelled_from'
);
SET @ddl := IF(@has_cancelled_from = 0,
    'ALTER TABLE course_section_schedules ADD COLUMN cancelled_from DATE NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE course_section_recurring_schedules
    MODIFY COLUMN day_of_week INT NOT NULL;

SET @has_day_constraint := (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'course_section_recurring_schedules'
      AND constraint_name = 'chk_recurring_schedule_day_of_week'
      AND constraint_type = 'CHECK'
);
SET @ddl := IF(@has_day_constraint = 0,
    'ALTER TABLE course_section_recurring_schedules ADD CONSTRAINT chk_recurring_schedule_day_of_week CHECK (day_of_week BETWEEN 1 AND 7)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
