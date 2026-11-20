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
public class CourseAdvisorPlanDto {
    private String title;
    private String reason;
    private List<CourseRecommendationItemDto> courses;
    private Integer totalCredits;
}
