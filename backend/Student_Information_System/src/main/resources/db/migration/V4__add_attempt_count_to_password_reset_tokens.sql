-- Migration V4: Add attempt_count for OTP brute-force protection
ALTER TABLE password_reset_tokens
ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
