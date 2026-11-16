package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamScheduleRequest;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ExamScheduleService {

    Page<ExamScheduleDto> searchSchedules(
            String keyword,
            Long semesterId,
            ExamScheduleStatus status,
            ExamType examType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    ExamScheduleDto getScheduleById(Long id);

    ExamScheduleDto createSchedule(ExamScheduleRequest request);

    ExamScheduleDto updateSchedule(Long id, ExamScheduleRequest request);

    void deleteSchedule(Long id);

    ExamScheduleDto cancelSchedule(Long id);

    List<ExamStudentDto> getStudentsByScheduleId(Long id);

    List<ExamScheduleDto> getStudentSchedules(String username, Long semesterId, ExamScheduleStatus status);

    ExamScheduleDto getStudentScheduleDetail(String username, Long scheduleId);
}

