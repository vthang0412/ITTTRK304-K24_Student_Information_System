package com.example.student_management.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictingStudentDto {
    private Long studentId;
    private String studentCode;
    private String fullName;
    private String conflictingCourseCode;
    private String conflictingCourseName;
    private String conflictingSectionCode;
    private LocalDate conflictingExamDate;
    private LocalTime conflictingStartTime;
    private LocalTime conflictingEndTime;
}

