package com.example.student_management.dto.academic;

import java.time.LocalDateTime;

public record BulkEnrollmentResult(
        Long enrollmentId,
        String studentName,
        String studentCode,
        String sectionCode,
        boolean success,
        String message,
        Boolean scheduleConfigured,
        String courseCode,
        String courseName,
        String semesterName,
        String action,
        String resultStatus,
        String rejectionReason,
        String studentEmail,
        LocalDateTime registeredAt
) {
}
