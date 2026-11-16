package com.example.student_management.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableOccurrenceDto {
    private String id;
    private Long enrollmentId;
    private Long sectionId;
    private Long courseId;
    private Long semesterId;
    private String semesterCode;
    private String semesterName;
    private String courseCode;
    private String courseName;
    private Integer credits;
    private String sectionCode;
    private String sectionStatus;
    private String lecturerName;
    private String room;
    private LocalDate date;
    private int dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer startPeriod;
    private Integer endPeriod;
}
