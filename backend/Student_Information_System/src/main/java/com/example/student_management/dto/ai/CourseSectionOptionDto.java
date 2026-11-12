package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSectionOptionDto {
    private Long sectionId;
    private String sectionCode;
    private String lecturerName;
    private String schedule;
    private Integer capacity;
    private Long enrolledCount;
    private Integer remainingSeats;
    private String status;
    private boolean isOpen;
    private boolean hasScheduleConflict;
    private String conflictWith;
}

