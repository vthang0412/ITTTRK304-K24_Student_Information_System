package com.example.student_management.dto.academic;

import com.example.student_management.entity.academic.RecurringCourseSchedule;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;

@Value
@Builder
public class RecurringScheduleSessionResponse {
    Long id;
    int dayOfWeek;
    int startPeriod;
    int endPeriod;
    LocalTime startTime;
    LocalTime endTime;
    String classroom;
    LocalDate startDate;
    LocalDate endDate;

    public static RecurringScheduleSessionResponse from(RecurringCourseSchedule schedule) {
        return RecurringScheduleSessionResponse.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .startPeriod(schedule.getStartPeriod())
                .endPeriod(schedule.getEndPeriod())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .classroom(schedule.getClassroom())
                .startDate(schedule.getStartDate())
                .endDate(schedule.getEndDate())
                .build();
    }
}
