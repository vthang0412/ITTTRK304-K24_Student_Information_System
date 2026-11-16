package com.example.student_management.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(regexp = "PENDING|COMPLETED|SKIPPED")
    private String status;

    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    private LocalDate studyDate;

    @Min(1)
    @Max(1440)
    private Integer durationMinutes;
}

