package com.example.student_management.dto.academic;

import java.util.List;

public record BulkEnrollmentHistoryDetailDto(
        BulkEnrollmentHistoryDto operation,
        List<BulkEnrollmentResult> results
) {
}
