package com.example.student_management.dto.academic;

import java.util.List;

public record BulkEnrollmentResponse(
        int requestedCount,
        int successCount,
        int failureCount,
        int skippedCount,
        List<BulkEnrollmentResult> results
) {
}
