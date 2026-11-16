CREATE TABLE gradebook_bulk_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_code VARCHAR(36) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(20) NOT NULL,
    selected_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gradebook_bulk_operations_code (operation_code),
    KEY idx_gradebook_bulk_operations_actor (actor),
    KEY idx_gradebook_bulk_operations_action (action),
    KEY idx_gradebook_bulk_operations_created (created_at)
);

CREATE TABLE gradebook_bulk_operation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id BIGINT NOT NULL,
    gradebook_id BIGINT NULL,
    course_code VARCHAR(100) NULL,
    course_name VARCHAR(255) NULL,
    section_code VARCHAR(100) NULL,
    semester_name VARCHAR(255) NULL,
    previous_status VARCHAR(30) NULL,
    status VARCHAR(30) NULL,
    action VARCHAR(20) NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_gradebook_bulk_items_operation (operation_id),
    KEY idx_gradebook_bulk_items_gradebook (gradebook_id),
    KEY idx_gradebook_bulk_items_course (course_code),
    KEY idx_gradebook_bulk_items_section (section_code),
    KEY idx_gradebook_bulk_items_status (result_status),
    CONSTRAINT fk_gradebook_bulk_items_operation
        FOREIGN KEY (operation_id) REFERENCES gradebook_bulk_operations (id) ON DELETE CASCADE
);
