
CREATE TABLE feedbacks (
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

    CONSTRAINT fk_feedbacks_student
        FOREIGN KEY (student_id) REFERENCES students(id),

    CONSTRAINT fk_feedbacks_lecturer
        FOREIGN KEY (assigned_lecturer_id) REFERENCES lecturers(id),

    CONSTRAINT fk_feedbacks_course_section
        FOREIGN KEY (course_section_id) REFERENCES course_sections(id)
);

CREATE TABLE feedback_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_feedback_messages_feedback_id (feedback_id),
    INDEX idx_feedback_messages_sender_id (sender_id),

    CONSTRAINT fk_feedback_messages_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks(id),

    CONSTRAINT fk_feedback_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(id)
);
