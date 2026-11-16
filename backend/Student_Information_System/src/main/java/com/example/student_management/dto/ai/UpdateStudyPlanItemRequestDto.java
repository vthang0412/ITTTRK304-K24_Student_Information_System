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
public class UpdateStudyPlanItemRequestDto {
    private String status; // PENDING, COMPLETED, SKIPPED
    private String title;
    private String description;
    private LocalDate studyDate;
    private Integer durationMinutes;
}

