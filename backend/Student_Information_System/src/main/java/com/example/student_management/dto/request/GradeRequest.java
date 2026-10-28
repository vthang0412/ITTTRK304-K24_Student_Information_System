package com.example.student_management.dto.request;



import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class GradeRequest {
    @DecimalMin(value = "0.0", message = "Score must be >= 0")
    @DecimalMax(value = "10.0", message = "Score must be <= 10")
    private BigDecimal componentScore;

    @DecimalMin(value = "0.0", message = "Score must be >= 0")
    @DecimalMax(value = "10.0", message = "Score must be <= 10")
    private BigDecimal finalScore;

    private Map<Long, BigDecimal> componentScores;
}
