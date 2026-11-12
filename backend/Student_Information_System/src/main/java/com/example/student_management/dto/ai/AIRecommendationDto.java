package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRecommendationDto {
    private String type; // STRENGTH, IMPROVEMENT, WARNING, ACTION
    private String title;
    private String description;
    private String relatedCourseCode;
    private String priority; // HIGH, MEDIUM, LOW
}

