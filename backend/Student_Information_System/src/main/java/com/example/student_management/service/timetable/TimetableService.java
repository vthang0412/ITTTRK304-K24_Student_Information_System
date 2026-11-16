package com.example.student_management.service.timetable;

import com.example.student_management.dto.timetable.TimetableWeekResponse;

import java.time.LocalDate;

public interface TimetableService {
    TimetableWeekResponse getWeek(String username, Long semesterId, LocalDate weekStart, int page, int size);

    TimetableWeekResponse getAdminWeek(Long semesterId,
                                       Long sectionId,
                                       Long lecturerId,
                                       String classroom,
                                       LocalDate weekStart,
                                       int page,
                                       int size);
}
