package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanItemDto {
    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String title;
    private String description;
    private LocalDate studyDate;
    private Integer durationMinutes;
    private String status; // PENDING, COMPLETED, SKIPPED
}

