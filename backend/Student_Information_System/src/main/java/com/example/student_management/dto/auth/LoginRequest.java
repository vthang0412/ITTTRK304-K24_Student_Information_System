package com.example.student_management.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    @Pattern(regexp = "^\\S+$", message = "Tên đăng nhập không được chứa khoảng trắng")
    private String username;

    @Pattern(regexp = "^\\S+$", message = "Email không được chứa khoảng trắng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
