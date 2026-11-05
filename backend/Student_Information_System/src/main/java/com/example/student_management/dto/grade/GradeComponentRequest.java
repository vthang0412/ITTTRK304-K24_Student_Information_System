package com.example.student_management.dto.grade;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeComponentRequest {
    @NotBlank(message = "Component name is required")
    @Size(max = 100)
    private String componentName;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", message = "Weight must be >= 0")
    @DecimalMax(value = "100.0", message = "Weight must be <= 100")
    private BigDecimal weight;

    @DecimalMin(value = "0.01", message = "Max score must be > 0")
    @DecimalMax(value = "100.0", message = "Max score must be <= 100")
    private BigDecimal maxScore;

    private String componentType;

    private Integer displayOrder;

    private Boolean required;
}
