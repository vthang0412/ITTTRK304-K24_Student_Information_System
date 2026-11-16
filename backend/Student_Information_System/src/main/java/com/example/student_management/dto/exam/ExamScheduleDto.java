package com.example.student_management.dto.exam;

import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamScheduleDto {
    private Long id;
    private Long courseSectionId;
    private String sectionCode;
    private String courseCode;
    private String courseName;
    private Integer credits;
    private Long semesterId;
    private String semesterName;
    private String lecturerName;

    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ExamType examType; // OFFLINE, ONLINE

    private Long roomId;
    private String roomCode;
    private String roomName;
    private Integer roomCapacity;
    private String building;

    private String examLocation;
    private String examLink;
    private ExamScheduleStatus status; // SCHEDULED, CANCELLED, COMPLETED
    private String notes;

    private int enrolledStudentsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

