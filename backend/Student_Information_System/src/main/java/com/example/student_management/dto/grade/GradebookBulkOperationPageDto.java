package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GradebookBulkOperationPageDto {
    List<GradebookBulkOperationDto> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
}
