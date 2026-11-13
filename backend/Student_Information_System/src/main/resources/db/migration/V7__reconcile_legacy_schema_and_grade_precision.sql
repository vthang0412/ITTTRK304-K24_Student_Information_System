-- ============================================================
-- V7: Reconcile legacy schemas and fix grade precision.
--
-- Why this migration exists
-- -------------------------
-- Databases created by Hibernate (development) have all tables but no
-- flyway_schema_history. When such a database is baselined, the old
-- V2/V4/V5 scripts are not idempotent and fail. Starting with this
-- release, existing non-empty databases are baselined at version 6
-- (see spring.flyway.baseline-version in application-prod.properties),
-- so V2..V6 are skipped for those databases. This script then
-- re-creates/extends anything those scripts would have created.
--
-- It is intentionally idempotent: it is also executed on a brand-new
-- database (after V1..V6) without changing the result.
--
-- Data is never deleted. Missing structures are only added.
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- "CREATE TABLE IF NOT EXISTS" on an existing table emits MySQL note 1050.
-- It is expected here (fresh databases already have these tables from
-- V2/V3/V6); suppress only notes so the migration log stays readable.
-- Errors and warnings are unaffected.
SET @old_sql_notes = @@sql_notes;
SET sql_notes = 0;

-- ------------------------------------------------------------
-- 1) M-03: allow storing a score of 100.00 (precision 5, scale 2).
--    The JPA entities still declare precision 4/scale 2; Hibernate
--    schema validation does not compare precision, so widening the
--    columns here is safe and keeps the entities untouched.
-- ------------------------------------------------------------
ALTER TABLE grade_components MODIFY COLUMN max_score DECIMAL(5,2) NOT NULL;
ALTER TABLE grade_component_scores MODIFY COLUMN score DECIMAL(5,2) NOT NULL;
ALTER TABLE grades MODIFY COLUMN component_score DECIMAL(5,2) NULL;
ALTER TABLE grades MODIFY COLUMN final_score DECIMAL(5,2) NULL;

-- ------------------------------------------------------------
-- 2) Tables normally created by V2 (feedbacks).
--    CREATE TABLE IF NOT EXISTS keeps this safe for fresh databases.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    student_id BIGINT NOT NULL,
    assigned_lecturer_id BIGINT NULL,
    course_section_id BIGINT NULL,
    admin_note TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_feedbacks_student_id (student_id),
    INDEX idx_feedbacks_assigned_lecturer_id (assigned_lecturer_id),
    INDEX idx_feedbacks_course_section_id (course_section_id),
    CONSTRAINT fk_feedbacks_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_feedbacks_lecturer FOREIGN KEY (assigned_lecturer_id) REFERENCES lecturers (id),
    CONSTRAINT fk_feedbacks_course_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_feedback_messages_feedback_id (feedback_id),
    INDEX idx_feedback_messages_sender_id (sender_id),
    CONSTRAINT fk_feedback_messages_feedback FOREIGN KEY (feedback_id) REFERENCES feedbacks (id),
    CONSTRAINT fk_feedback_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 3) password_reset_tokens (V3 + V4). If the table already exists
--    without attempt_count, add the column.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_attempt_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'password_reset_tokens'
      AND column_name = 'attempt_count'
);
SET @ddl := IF(@has_attempt_count = 0,
    'ALTER TABLE password_reset_tokens ADD COLUMN attempt_count INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4) Student profile columns (V5). Add only the missing ones.
-- ------------------------------------------------------------
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'phone'
);
SET @ddl := IF(@has_col = 0, 'ALTER TABLE students ADD COLUMN phone VARCHAR(20) NULL', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'class_name'
);
SET @ddl := IF(@has_col = 0, 'ALTER TABLE students ADD COLUMN class_name VARCHAR(50) NULL', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'major'
);
SET @ddl := IF(@has_col = 0, 'ALTER TABLE students ADD COLUMN major VARCHAR(100) NULL', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'academic_status'
);
SET @ddl := IF(@has_col = 0, 'ALTER TABLE students ADD COLUMN academic_status VARCHAR(30) NULL', 'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5) AI assistant tables (V6). All of them are recreated here so a
--    database baselined at version 6 also gets the planner tables.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_ai_conv_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_ai_conv_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_msg_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id) ON DELETE CASCADE,
    INDEX idx_ai_msg_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_study_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    goal TEXT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_plan_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_ai_plan_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_study_plan_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    study_date DATE NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_ai_item_plan FOREIGN KEY (plan_id) REFERENCES ai_study_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_item_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE SET NULL,
    INDEX idx_ai_item_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    feature_type VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_log_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_ai_log_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 5b) Reconcile FK delete rules that differ between a
--     Hibernate-created database (NO ACTION) and the Flyway
--     baseline (V3/V6 use ON DELETE CASCADE). Without this,
--     deleting a user/conversation with child rows behaves
--     differently in production than on a fresh database.
--     Safe here: only the rule changes, no rows are touched.
-- ------------------------------------------------------------
SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'password_reset_tokens'
      AND k.column_name = 'user_id' AND k.referenced_table_name = 'users'
    LIMIT 1
);
SET @rule := (
    SELECT r.delete_rule
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'password_reset_tokens'
      AND k.column_name = 'user_id' AND k.referenced_table_name = 'users'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NOT NULL AND @rule <> 'CASCADE',
    CONCAT('ALTER TABLE password_reset_tokens DROP FOREIGN KEY `', @fk_name, '`'),
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'password_reset_tokens'
      AND k.column_name = 'user_id' AND k.referenced_table_name = 'users'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NULL,
    'ALTER TABLE password_reset_tokens ADD CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_conversations'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @rule := (
    SELECT r.delete_rule
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_conversations'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NOT NULL AND @rule <> 'CASCADE',
    CONCAT('ALTER TABLE ai_conversations DROP FOREIGN KEY `', @fk_name, '`'),
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_conversations'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NULL,
    'ALTER TABLE ai_conversations ADD CONSTRAINT fk_ai_conv_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_messages'
      AND k.column_name = 'conversation_id' AND k.referenced_table_name = 'ai_conversations'
    LIMIT 1
);
SET @rule := (
    SELECT r.delete_rule
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_messages'
      AND k.column_name = 'conversation_id' AND k.referenced_table_name = 'ai_conversations'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NOT NULL AND @rule <> 'CASCADE',
    CONCAT('ALTER TABLE ai_messages DROP FOREIGN KEY `', @fk_name, '`'),
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_messages'
      AND k.column_name = 'conversation_id' AND k.referenced_table_name = 'ai_conversations'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NULL,
    'ALTER TABLE ai_messages ADD CONSTRAINT fk_ai_msg_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id) ON DELETE CASCADE',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_usage_logs'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @rule := (
    SELECT r.delete_rule
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_usage_logs'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NOT NULL AND @rule <> 'CASCADE',
    CONCAT('ALTER TABLE ai_usage_logs DROP FOREIGN KEY `', @fk_name, '`'),
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_name := (
    SELECT k.constraint_name
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints r
      ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE() AND k.table_name = 'ai_usage_logs'
      AND k.column_name = 'student_id' AND k.referenced_table_name = 'students'
    LIMIT 1
);
SET @ddl := IF(@fk_name IS NULL,
    'ALTER TABLE ai_usage_logs ADD CONSTRAINT fk_ai_log_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 6) Indexes for the hottest queries. Created only when missing.
-- ------------------------------------------------------------
SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'enrollments'
      AND index_name = 'idx_enrollments_section_status'
);
SET @ddl := IF(@has_idx = 0,
    'CREATE INDEX idx_enrollments_section_status ON enrollments (section_id, status)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'grade_audit_logs'
      AND index_name = 'idx_grade_audit_gradebook_created'
);
SET @ddl := IF(@has_idx = 0,
    'CREATE INDEX idx_grade_audit_gradebook_created ON grade_audit_logs (gradebook_id, created_at)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'feedbacks'
      AND index_name = 'idx_feedbacks_created_at'
);
SET @ddl := IF(@has_idx = 0,
    'CREATE INDEX idx_feedbacks_created_at ON feedbacks (created_at)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'password_reset_tokens'
      AND index_name = 'idx_prt_user_used_created'
);
SET @ddl := IF(@has_idx = 0,
    'CREATE INDEX idx_prt_user_used_created ON password_reset_tokens (user_id, used, created_at)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 7) Business-rule CHECK constraints.
--    Each constraint is added only when the existing data already
--    satisfies it, so an upgrade never fails because of legacy data.
--    Invalid rows are reported by the audit queries, never deleted.
-- ------------------------------------------------------------
SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'semesters'
      AND constraint_name = 'chk_semesters_dates'
);
SET @bad := (SELECT COUNT(*) FROM semesters WHERE end_date <= start_date);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE semesters ADD CONSTRAINT chk_semesters_dates CHECK (end_date > start_date)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'semesters'
      AND constraint_name = 'chk_semesters_registration_dates'
);
SET @bad := (
    SELECT COUNT(*) FROM semesters
    WHERE registration_start IS NOT NULL AND registration_end IS NOT NULL
      AND registration_end <= registration_start
);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE semesters ADD CONSTRAINT chk_semesters_registration_dates CHECK (registration_start IS NULL OR registration_end IS NULL OR registration_end > registration_start)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'course_sections'
      AND constraint_name = 'chk_course_sections_capacity'
);
SET @bad := (SELECT COUNT(*) FROM course_sections WHERE capacity <= 0);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE course_sections ADD CONSTRAINT chk_course_sections_capacity CHECK (capacity > 0)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'grade_components'
      AND constraint_name = 'chk_grade_components_max_score'
);
SET @bad := (SELECT COUNT(*) FROM grade_components WHERE max_score <= 0 OR max_score > 1000);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE grade_components ADD CONSTRAINT chk_grade_components_max_score CHECK (max_score > 0 AND max_score <= 1000)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'grade_components'
      AND constraint_name = 'chk_grade_components_weight'
);
SET @bad := (SELECT COUNT(*) FROM grade_components WHERE weight < 0 OR weight > 100);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE grade_components ADD CONSTRAINT chk_grade_components_weight CHECK (weight >= 0 AND weight <= 100)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'grade_component_scores'
      AND constraint_name = 'chk_grade_component_scores_score'
);
SET @bad := (SELECT COUNT(*) FROM grade_component_scores WHERE score < 0 OR score > 1000);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE grade_component_scores ADD CONSTRAINT chk_grade_component_scores_score CHECK (score >= 0 AND score <= 1000)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'grades'
      AND constraint_name = 'chk_grades_scores'
);
SET @bad := (
    SELECT COUNT(*) FROM grades
    WHERE (component_score IS NOT NULL AND component_score < 0)
       OR (final_score IS NOT NULL AND final_score < 0)
);
SET @ddl := IF(@has_chk = 0 AND @bad = 0,
    'ALTER TABLE grades ADD CONSTRAINT chk_grades_scores CHECK ((component_score IS NULL OR component_score >= 0) AND (final_score IS NULL OR final_score >= 0))',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET sql_notes = @old_sql_notes;
