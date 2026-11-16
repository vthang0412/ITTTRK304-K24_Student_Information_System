package com.example.student_management.controller.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamScheduleRequest;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import com.example.student_management.service.exam.ExamScheduleService;
import com.example.student_management.service.exam.ExamExcelExportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/exam-schedules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExamScheduleController {

    @Autowired
    private ExamScheduleService examScheduleService;

    @Autowired
    private ExamExcelExportService examExcelExportService;

    /**
     * 1. Danh sách lịch thi có phân trang, tìm kiếm và lọc
     */
    @GetMapping
    public ResponseEntity<Page<ExamScheduleDto>> searchSchedules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) ExamScheduleStatus status,
            @RequestParam(required = false) ExamType examType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "examDate,asc") String sort
    ) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = (sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1]))
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));
        return ResponseEntity.ok(examScheduleService.searchSchedules(keyword, semesterId, courseId, status, examType, fromDate, toDate, pageRequest));
    }

    @GetMapping("/course-sections/{sectionId}/eligible-student-count")
    public ResponseEntity<Map<String, Long>> getEligibleStudentCount(@PathVariable Long sectionId) {
        return ResponseEntity.ok(Map.of("count", examScheduleService.getEligibleStudentCount(sectionId)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSchedules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) ExamScheduleStatus status,
            @RequestParam(required = false) ExamType examType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        byte[] file = examExcelExportService.exportSchedules(
                examScheduleService.getSchedulesForExport(keyword, semesterId, courseId, status, examType, fromDate, toDate));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Exam_Schedules.xlsx\"")
                .body(file);
    }

    /**
     * 2. Chi tiết lịch thi
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExamScheduleDto> getScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(examScheduleService.getScheduleById(id));
    }

    /**
     * 3. Tạo mới lịch thi (kiểm tra đầy đủ xung đột phòng, sinh viên và sức chứa)
     */
    @PostMapping
    public ResponseEntity<ExamScheduleDto> createSchedule(@Valid @RequestBody ExamScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examScheduleService.createSchedule(request));
    }

    /**
     * 4. Cập nhật lịch thi
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExamScheduleDto> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody ExamScheduleRequest request
    ) {
        return ResponseEntity.ok(examScheduleService.updateSchedule(id, request));
    }

    /**
     * 5. Xóa lịch thi
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(@PathVariable Long id) {
        examScheduleService.deleteSchedule(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Xóa lịch thi thành công");
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Hủy lịch thi (chuyển trạng thái sang CANCELLED)
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ExamScheduleDto> cancelSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(examScheduleService.cancelSchedule(id));
    }

    /**
     * 7. Lấy danh sách sinh viên dự thi của lịch thi
     */
    @GetMapping("/{id}/students")
    public ResponseEntity<List<ExamStudentDto>> getStudentsByScheduleId(@PathVariable Long id) {
        return ResponseEntity.ok(examScheduleService.getStudentsByScheduleId(id));
    }

    @GetMapping("/{id}/students/export")
    public ResponseEntity<byte[]> exportStudentsByScheduleId(@PathVariable Long id) {
        ExamScheduleDto schedule = examScheduleService.getScheduleById(id);
        String context = "Môn học: " + schedule.getCourseName() + " (" + schedule.getCourseCode() + ")"
                + " | Lớp: " + schedule.getSectionCode()
                + " | Học kỳ: " + schedule.getSemesterName();
        byte[] file = examExcelExportService.exportStudents(examScheduleService.getStudentsByScheduleId(id), context);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Exam_Students_" + id + ".xlsx\"")
                .body(file);
    }
}

