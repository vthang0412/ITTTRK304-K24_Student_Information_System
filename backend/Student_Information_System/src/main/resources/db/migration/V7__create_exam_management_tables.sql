-- ============================================================
-- Migration V7: Create Exam Management Tables
-- Module: Exam Rooms and Exam Schedules
-- ============================================================

CREATE TABLE IF NOT EXISTS exam_rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(50) NOT NULL,
    room_name VARCHAR(255) NOT NULL,
    building VARCHAR(100) NULL,
    capacity INT NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT uk_exam_room_code UNIQUE (room_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_section_id BIGINT NOT NULL,
    exam_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    exam_type VARCHAR(20) NOT NULL,
    room_id BIGINT NULL,
    exam_location VARCHAR(255) NULL,
    exam_link VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_exam_sched_section FOREIGN KEY (course_section_id) REFERENCES course_sections(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_sched_room FOREIGN KEY (room_id) REFERENCES exam_rooms(id) ON DELETE RESTRICT,
    INDEX idx_exam_sched_date (exam_date),
    INDEX idx_exam_sched_section (course_section_id),
    INDEX idx_exam_sched_room (room_id),
    INDEX idx_exam_sched_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

