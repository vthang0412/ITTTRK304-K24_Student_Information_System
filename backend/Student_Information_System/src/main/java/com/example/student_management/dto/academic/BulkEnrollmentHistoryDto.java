package com.example.student_management.dto.academic;

import java.time.LocalDateTime;

public record BulkEnrollmentHistoryDto(
        Long id,
        String batchCode,
        String adminUsername,
        LocalDateTime createdAt,
        String action,
        Long semesterId,
        String semesterName,
        int selectedCount,
        int successCount,
        int failureCount,
        int skippedCount,
        String rejectionReason
) {
}
