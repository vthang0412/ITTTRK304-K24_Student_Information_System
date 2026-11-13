package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GradebookBulkActionItemResult {
    Long gradebookId;
    String courseCode;
    String courseName;
    String sectionCode;
    String semesterName;
    String previousStatus;
    String status;
    boolean successful;
    boolean skipped;
    String message;
}
