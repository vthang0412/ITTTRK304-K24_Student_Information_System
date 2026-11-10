package com.example.student_management.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    private String email;

    private String otp;
    private String token;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")
    private String newPassword;

    public String getEffectiveOtp() {
        if (otp != null && !otp.isBlank()) {
            return otp.trim();
        }
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return "";
    }
}
