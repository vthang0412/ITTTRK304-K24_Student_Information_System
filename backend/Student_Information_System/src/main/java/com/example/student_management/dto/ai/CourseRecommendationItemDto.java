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
public class CourseRecommendationItemDto {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private Integer credits;
    private List<String> prerequisites;
    private boolean prerequisitesSatisfied;
    private List<String> missingPrerequisites;
    private String status; // RECOMMENDED, MUST_RETAKE, SCHEDULE_CONFLICT, SECTIONS_FULL, PREREQUISITE_NOT_MET, ALREADY_COMPLETED, CURRENTLY_ENROLLED
    private String priority; // HIGH, MEDIUM, LOW, NONE
    private String reason;
    private List<CourseSectionOptionDto> availableSections;
}

