package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorResponseDto {
    private String studentCode;
    private String fullName;
    private String department;
    private String major;
    private int accumulatedCredits;
    private int activeCredits;
    private int maxCreditsPerSemester;

    private List<CourseRecommendationItemDto> recommendedCourses;
    private List<CourseRecommendationItemDto> notRecommendedCourses;

    private String advisorSummary;
    private String disclaimer;
    private LocalDateTime generatedAt;
}

