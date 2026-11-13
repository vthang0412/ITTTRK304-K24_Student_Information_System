package com.example.student_management.dto.auth;

import com.example.student_management.entity.auth.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentAccountCreateRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Size(max = 100)
    private String department;

    private AccountStatus status = AccountStatus.ACTIVE;
}
