package com.example.student_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradebookSummaryDto {
    private Long gradebookId;
    private Long sectionId;
    private String sectionCode;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private Long semesterId;
    private String semesterName;
    private Long lecturerId;
    private String lecturerName;
    private long totalStudents;
    private long completedStudents;
    private long incompleteStudents;
    private String status;
    private String gradingMode;
    private int componentCount;
    private LocalDateTime lockedAt;
    private String lockedBy;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private LocalDateTime publishedAt;
    private String publishedBy;
}
