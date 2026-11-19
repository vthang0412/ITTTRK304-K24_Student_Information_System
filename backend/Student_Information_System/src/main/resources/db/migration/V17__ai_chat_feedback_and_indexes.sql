ALTER TABLE ai_messages
    ADD COLUMN feedback VARCHAR(20) NULL;

ALTER TABLE ai_usage_logs
    ADD INDEX idx_ai_log_student_created (student_id, created_at);

ALTER TABLE ai_conversations
    ADD INDEX idx_ai_conv_student_updated (student_id, updated_at);
