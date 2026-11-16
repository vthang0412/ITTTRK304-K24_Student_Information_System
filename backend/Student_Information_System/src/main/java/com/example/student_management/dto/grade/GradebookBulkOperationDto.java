package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class GradebookBulkOperationDto {
    Long id;
    String operationCode;
    String actor;
    String action;
    int selectedCount;
    int successCount;
    int failureCount;
    int skippedCount;
    LocalDateTime createdAt;
    LocalDateTime completedAt;
    List<GradebookBulkOperationItemDto> items;
}
