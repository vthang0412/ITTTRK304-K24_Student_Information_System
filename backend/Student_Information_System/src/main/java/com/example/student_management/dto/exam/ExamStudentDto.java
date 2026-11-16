package com.example.student_management.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStudentDto {
    private Long studentId;
    private String studentCode;
    private String fullName;
    private String email;
    private String className;
    private String department;
    private LocalDateTime registeredAt;
}

