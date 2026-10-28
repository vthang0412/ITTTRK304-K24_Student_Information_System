package com.example.student_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeAuditLogDto {
    private Long id;
    private String actor;
    private String action;
    private String oldValue;
    private String newValue;
    private String reason;
    private LocalDateTime createdAt;
    private Long enrollmentId;
    private String studentCode;
    private String studentName;
    private String componentName;
}
