package com.example.student_management.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {
    // Chỉ trả về thông điệp chung, không trả OTP/token/email để tránh lộ bí mật
    // và tránh dò quét sự tồn tại của tài khoản (account enumeration).
    private String message;
}
