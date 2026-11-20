package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorRequestAnalysisDto {
    private String careerOrientation;
    private String targetCreditText;
    private Integer targetCreditsMin;
    private Integer targetCreditsMax;
    private Integer effectiveCreditsMin;
    private Integer effectiveCreditsMax;
    private String customNote;
    private List<String> detectedPreferences;
    private Integer completedCourses;
    private Integer failedCourses;
    private Integer currentlyEnrolledCourses;
    private String academicSummary;
}
