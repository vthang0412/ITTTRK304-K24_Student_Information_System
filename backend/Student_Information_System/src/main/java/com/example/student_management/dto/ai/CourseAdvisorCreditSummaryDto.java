package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorCreditSummaryDto {
    private Integer recommendedCourseCount;
    private Integer totalRecommendedCredits;
    private Integer targetCreditsMin;
    private Integer targetCreditsMax;
    private Integer creditDifference;
    private boolean withinTargetRange;
    private boolean limitedByMaxCredits;
    private Integer maxCreditsPerSemester;
    private String explanation;
}
