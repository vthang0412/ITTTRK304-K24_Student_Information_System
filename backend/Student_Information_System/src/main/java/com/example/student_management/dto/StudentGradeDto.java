package com.example.student_management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentGradeDto {
    private Long gradebookId;
    private Long enrollmentId;
    private Long sectionId;
    private String sectionCode;
    private String courseCode;
    private String courseName;
    private Integer credits;
    private String semesterName;
    private BigDecimal componentScore;
    private BigDecimal finalScore;
    private List<StudentComponentScoreDto> componentScores;
    private LocalDateTime publishedAt;
}
