package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudyPlanRequestDto {
    private String title;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Long> focusCourseIds;
    private Integer targetHoursPerDay;
    private String customNote;
    private List<StudyPlanItemDto> manualItems;
}

