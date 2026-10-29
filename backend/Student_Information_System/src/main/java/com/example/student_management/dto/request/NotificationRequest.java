package com.example.student_management.dto.request;

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
public class NotificationRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    @Size(max = 50)
    private String type; // SYSTEM, GRADE, ENROLLMENT, ACADEMIC, ANNOUNCEMENT

    private String targetRole; // STUDENT, LECTURER, ADMIN, hoặc null = ALL

    private Long recipientId; // Gửi cho 1 user cụ thể (nếu có)
}

