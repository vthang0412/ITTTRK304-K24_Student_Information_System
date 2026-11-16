package com.example.student_management.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorRequestDto {
    @Size(max = 255)
    private String careerOrientation;

    @Min(1)
    @Max(30)
    private Integer targetCredits;

    @Size(max = 2000)
    private String customNote;
}

