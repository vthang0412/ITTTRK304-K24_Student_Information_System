package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIInsightsDto {
    private String studentCode;
    private String fullName;
    private String department;
    private String major;
    private String className;

    private BigDecimal gpa10;
    private BigDecimal gpa4;
    private int completedCredits;
    private int activeCredits;
    private int totalCompletedCourses;
    private int totalFailedCourses;
    private String academicClassification;

    private List<AISemesterInsightDto> semesterInsights;
    private List<Map<String, Object>> lowScoreCourses;
    private String aiSummary;
    private List<AIRecommendationDto> recommendations;
    private LocalDateTime generatedAt;
}

