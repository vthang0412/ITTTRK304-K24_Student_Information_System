package com.example.student_management.controller.academic;

import com.example.student_management.dto.academic.RejectEnrollmentRequest;
import com.example.student_management.dto.academic.BulkEnrollmentRequest;
import com.example.student_management.dto.academic.BulkEnrollmentResponse;
import com.example.student_management.dto.academic.BulkEnrollmentPreviewRequest;
import com.example.student_management.dto.academic.BulkEnrollmentPreviewResponse;
import com.example.student_management.dto.academic.BulkEnrollmentHistoryDto;
import com.example.student_management.dto.academic.BulkEnrollmentHistoryDetailDto;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.service.academic.BulkEnrollmentService;
import com.example.student_management.service.academic.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class EnrollmentController {
    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private BulkEnrollmentService bulkEnrollmentService;

    @GetMapping("/enrollments/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Enrollment>> getMyEnrollments(Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(authentication.getName()));
    }

    @GetMapping("/enrollments/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Enrollment>> getPendingEnrollments() {
        return ResponseEntity.ok(enrollmentService.getPendingEnrollments());
    }

    @GetMapping("/enrollments/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Enrollment>> getEnrollmentsForAdmin(
            @RequestParam(required = false) String status) {
        EnrollmentStatus enrollmentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                enrollmentStatus = EnrollmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Trạng thái đăng ký không hợp lệ");
            }
        }
        return ResponseEntity.ok(enrollmentService.getEnrollmentsForAdmin(enrollmentStatus));
    }

    @PostMapping("/course-sections/{id}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Enrollment> enroll(@PathVariable("id") Long sectionId, Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.enroll(authentication.getName(), sectionId));
    }

    @PostMapping("/enrollments/{id:\\d+}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Enrollment> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.approve(authentication.getName(), id));
    }

    @PostMapping("/enrollments/{id:\\d+}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Enrollment> reject(@PathVariable Long id,
                                             @Valid @RequestBody RejectEnrollmentRequest request,
                                             Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.reject(authentication.getName(), id, request.getReason()));
    }

    @PostMapping("/enrollments/bulk-approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkEnrollmentResponse> approveBulk(
            @Valid @RequestBody BulkEnrollmentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bulkEnrollmentService.approve(
                authentication.getName(), request.getEnrollmentIds()));
    }

    @PostMapping("/enrollments/bulk-reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkEnrollmentResponse> rejectBulk(
            @Valid @RequestBody BulkEnrollmentRequest request,
            Authentication authentication) {
        if ((request.getReason() == null || request.getReason().isBlank())
                && (request.getReasonsByEnrollmentId() == null
                || !request.getReasonsByEnrollmentId().keySet().containsAll(request.getEnrollmentIds()))) {
            throw new BadRequestException("Vui lòng cung cấp lý do từ chối cho từng yêu cầu đã chọn");
        }
        return ResponseEntity.ok(bulkEnrollmentService.reject(
                authentication.getName(),
                request.getEnrollmentIds(),
                request.getReason(),
                request.getReasonsByEnrollmentId()));
    }

    @PostMapping("/enrollments/bulk-preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkEnrollmentPreviewResponse> previewBulk(
            @Valid @RequestBody BulkEnrollmentPreviewRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bulkEnrollmentService.preview(
                authentication.getName(), request.getEnrollmentIds(), request.getAction()));
    }

    @GetMapping("/enrollments/bulk-history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<BulkEnrollmentHistoryDto>> getBulkHistory(
            @RequestParam(required = false) String admin,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDateTime fromTime,
            @RequestParam(required = false) LocalDateTime toTime,
            @RequestParam(required = false) String student,
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BadRequestException("Thời điểm bắt đầu phải trước thời điểm kết thúc");
        }
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bulkEnrollmentService.findHistory(
                admin, semesterId, action, fromTime, toTime, student, course, section, result, pageable));
    }

    @GetMapping("/enrollments/bulk-history/{operationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkEnrollmentHistoryDetailDto> getBulkHistoryDetail(
            @PathVariable Long operationId) {
        return ResponseEntity.ok(bulkEnrollmentService.getHistory(operationId));
    }

    @DeleteMapping("/enrollments/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Enrollment> withdraw(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.withdraw(authentication.getName(), id));
    }
}
