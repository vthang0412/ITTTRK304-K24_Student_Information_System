package com.example.student_management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String email;

    @NotBlank
    private String password;
}
