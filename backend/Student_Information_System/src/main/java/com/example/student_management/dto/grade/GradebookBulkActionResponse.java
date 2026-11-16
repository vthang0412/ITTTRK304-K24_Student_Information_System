package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GradebookBulkActionResponse {
    String operationCode;
    int selectedCount;
    int successCount;
    int failureCount;
    int skippedCount;
    List<GradebookBulkActionItemResult> results;
}
