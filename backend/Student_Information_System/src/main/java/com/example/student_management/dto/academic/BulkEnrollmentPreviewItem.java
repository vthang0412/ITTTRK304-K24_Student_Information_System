package com.example.student_management.dto.academic;

public record BulkEnrollmentPreviewItem(
        Long enrollmentId,
        String studentCode,
        String studentName,
        String studentEmail,
        String courseCode,
        String courseName,
        String sectionCode,
        String semesterName,
        String enrollmentStatus,
        boolean eligible,
        String resultStatus,
        String message,
        Boolean scheduleConfigured
) {
}
