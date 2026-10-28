package com.example.student_management.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 150)
    private String email;

    @Size(max = 100)
    private String department;

    private String currentPassword;

    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
