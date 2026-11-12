package com.example.student_management.dto.academic;

import java.util.List;

public record BulkEnrollmentPreviewResponse(
        int requestedCount,
        int eligibleCount,
        int ineligibleCount,
        int skippedCount,
        List<BulkEnrollmentPreviewItem> results
) {
}
