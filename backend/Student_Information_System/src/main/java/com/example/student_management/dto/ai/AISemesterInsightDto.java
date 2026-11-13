package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AISemesterInsightDto {
    private String semesterName;
    private BigDecimal gpa10;
    private BigDecimal gpa4;
    private int creditsEarned;
    private int coursesCompleted;
    private int coursesFailed;
    private String trend; // FIRST, UP, DOWN, STABLE
}

