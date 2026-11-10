package com.example.student_management.dto.grade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradebookDetailDto {
    private Long gradebookId;
    private Long sectionId;
    private String sectionCode;
    private String courseCode;
    private String courseName;
    private String semesterName;
    private String status;
    private String gradingMode;
    private String lecturerName;
    private List<GradeComponentDto> components;
    private List<GradebookEnrollmentDto> enrollments;
}
