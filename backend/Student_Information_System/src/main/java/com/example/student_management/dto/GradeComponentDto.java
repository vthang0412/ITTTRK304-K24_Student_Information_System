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
public class GradeComponentDto {
    private Long id;
    private String componentName;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private String componentType;
    private Integer displayOrder;
    private Boolean required;
}
