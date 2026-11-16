package com.example.student_management.dto.ai;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudyPlanRequestDto {
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String goal;

    @Pattern(regexp = "ACTIVE|COMPLETED|ARCHIVED")
    private String status;
}

