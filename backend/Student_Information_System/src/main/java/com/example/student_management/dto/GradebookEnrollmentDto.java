package com.example.student_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradebookEnrollmentDto {
    private Long enrollmentId;
    private Long studentId;
    private String studentCode;
    private String fullName;
    private String email;
    private Map<Long, BigDecimal> componentScores;
    private BigDecimal componentScore;
    private BigDecimal finalScore;
    private boolean complete;
}
