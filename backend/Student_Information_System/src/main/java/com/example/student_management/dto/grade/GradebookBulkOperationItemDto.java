package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GradebookBulkOperationItemDto {
    Long gradebookId;
    String courseCode;
    String courseName;
    String sectionCode;
    String semesterName;
    String previousStatus;
    String status;
    String action;
    String resultStatus;
    String message;
}
