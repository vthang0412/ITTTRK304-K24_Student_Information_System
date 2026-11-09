package com.example.student_management.service.email;

public interface EmailService {
    void sendOtpEmail(String toEmail, String fullName, String otp);
    void sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl);
    void sendPasswordResetSuccessEmail(String toEmail, String fullName);
}
