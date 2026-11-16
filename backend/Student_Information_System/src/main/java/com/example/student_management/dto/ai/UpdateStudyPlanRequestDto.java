package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudyPlanRequestDto {
    private String title;
    private String goal;
    private String status; // ACTIVE, COMPLETED, ARCHIVED
}

