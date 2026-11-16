package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GradebookBulkActionPreviewResponse {
    GradebookBulkAction action;
    int selectedCount;
    int eligibleCount;
    int ineligibleCount;
    List<GradebookBulkActionPreviewItem> results;
}
