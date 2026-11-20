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
public class CourseAdvisorSuitabilityDto {
    private List<String> priorityCourseCodes;
    private List<String> nextTermCourseCodes;
    private List<String> requiredConditions;
    private String explanation;
}
