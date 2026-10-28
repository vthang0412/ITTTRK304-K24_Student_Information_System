package com.example.student_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentComponentScoreDto {
    private Long componentId;
    private String componentName;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private BigDecimal score;
}
