package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanDto {
    private Long id;
    private Long studentId;
    private String studentCode;
    private String fullName;
    private String title;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // ACTIVE, COMPLETED, ARCHIVED
    private int totalTasks;
    private int completedTasks;
    private double completionRate;
    private int totalStudyMinutes;
    private List<StudyPlanItemDto> items;
    private String aiGeneratedAdvice;
    private LocalDateTime createdAt;
}

