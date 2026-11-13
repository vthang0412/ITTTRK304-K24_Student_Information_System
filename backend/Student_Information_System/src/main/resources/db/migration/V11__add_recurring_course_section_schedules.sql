ALTER TABLE course_section_schedules
    ADD COLUMN cancelled_from DATE NULL;

CREATE TABLE course_section_recurring_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_period INT NOT NULL,
    end_period INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    classroom VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    cancelled_from DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_recurring_schedules_section_dates (section_id, start_date, end_date),
    CONSTRAINT fk_recurring_schedules_section
        FOREIGN KEY (section_id) REFERENCES course_sections (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
