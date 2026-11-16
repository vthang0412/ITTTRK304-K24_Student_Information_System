-- Keep schedule revisions with their effective dates so past and future
-- weekly views use the schedule that was valid on each teaching date.
-- Existing free-text schedules are intentionally not backfilled: their
-- historical change dates cannot be recovered from the current database.
CREATE TABLE IF NOT EXISTS course_section_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    schedule VARCHAR(500) NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    PRIMARY KEY (id),
    INDEX idx_section_schedules_section_dates (section_id, effective_from, effective_to),
    CONSTRAINT fk_section_schedules_section
        FOREIGN KEY (section_id) REFERENCES course_sections (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
