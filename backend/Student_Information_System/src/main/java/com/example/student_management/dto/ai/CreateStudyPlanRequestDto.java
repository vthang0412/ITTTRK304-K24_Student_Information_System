package com.example.student_management.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String goal;

    private LocalDate startDate;
    private LocalDate endDate;

    private List<Long> focusCourseIds;

    @Min(1)
    @Max(24)
    private Integer targetHoursPerDay;

    @Size(max = 2000)
    private String customNote;

    private List<StudyPlanItemDto> manualItems;
}

