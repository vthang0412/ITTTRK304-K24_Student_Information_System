package com.example.student_management.controller.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.service.exam.ExamExcelExportService;
import com.example.student_management.service.exam.ExamScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/exam-schedules")
@PreAuthorize("hasRole('STUDENT')")
public class StudentExamScheduleController {

    @Autowired
    private ExamScheduleService examScheduleService;

    @Autowired
    private ExamExcelExportService examExcelExportService;

    /**
     * 1. Sinh viên tra cứu lịch thi cá nhân của mình
     * Backend lấy danh tính từ Security Context (JWT), sinh viên A không thể xem của sinh viên B
     */
    @GetMapping
    public ResponseEntity<List<ExamScheduleDto>> getMyExamSchedules(
            Authentication authentication,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) ExamScheduleStatus status
    ) {
        return ResponseEntity.ok(examScheduleService.getStudentSchedules(authentication.getName(), semesterId, status));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMyExamSchedules(
            Authentication authentication,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) ExamScheduleStatus status
    ) {
        List<ExamScheduleDto> schedules = examScheduleService.getStudentSchedules(authentication.getName(), semesterId, status);
        if (status == null) {
            schedules = new java.util.ArrayList<>(schedules);
            schedules.addAll(examScheduleService.getStudentSchedules(
                    authentication.getName(), semesterId, ExamScheduleStatus.CANCELLED));
        }
        byte[] file = examExcelExportService.exportSchedules(schedules);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"My_Exam_Schedules.xlsx\"")
                .body(file);
    }

    /**
     * 2. Xem chi tiết lịch thi cá nhân
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExamScheduleDto> getMyExamScheduleDetail(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(examScheduleService.getStudentScheduleDetail(authentication.getName(), id));
    }
}

