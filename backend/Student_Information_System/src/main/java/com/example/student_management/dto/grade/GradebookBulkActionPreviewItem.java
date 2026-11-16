package com.example.student_management.dto.grade;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GradebookBulkActionPreviewItem {
    Long gradebookId;
    String courseCode;
    String courseName;
    String sectionCode;
    String semesterName;
    String status;
    long totalStudents;
    long completedStudents;
    long incompleteStudents;
    long studentsWithAnyGrade;
    long studentsWithNoGrades;
    long studentsWithPartialGrades;
    boolean eligible;
    String message;
}
