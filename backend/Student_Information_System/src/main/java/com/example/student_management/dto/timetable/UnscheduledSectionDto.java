package com.example.student_management.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnscheduledSectionDto {
    private Long sectionId;
    private String sectionCode;
    private String courseCode;
    private String courseName;
}
