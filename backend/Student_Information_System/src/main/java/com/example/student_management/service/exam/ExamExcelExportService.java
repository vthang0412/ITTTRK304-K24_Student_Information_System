package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamStudentDto;

import java.util.List;

public interface ExamExcelExportService {
    byte[] exportSchedules(List<ExamScheduleDto> schedules);

    byte[] exportStudents(List<ExamStudentDto> students, String context);
}