package com.example.student_management.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableWeekResponse {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Long semesterId;
    private String semesterCode;
    private String semesterName;
    private int page;
    private int size;
    private int totalPages;
    private long totalElements;
    private List<TimetableOccurrenceDto> items;
    private List<UnscheduledSectionDto> unscheduledSections;
}
