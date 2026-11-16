package com.example.student_management.controller.grade;

import com.example.student_management.dto.grade.*;
import com.example.student_management.service.grade.GradebookBulkActionService;
import com.example.student_management.service.grade.GradebookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class GradebookController {
    @Autowired
    private GradebookService gradebookService;

    @Autowired
    private GradebookBulkActionService gradebookBulkActionService;

    @GetMapping("/admin/gradebooks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GradebookSummaryDto>> listGradebooks() {
        return ResponseEntity.ok(gradebookService.getGradebookSummaries());
    }

    @GetMapping("/admin/gradebooks/progress")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GradebookSummaryDto>> progress() {
        return ResponseEntity.ok(gradebookService.getGradebookSummaries());
    }

    @PostMapping("/admin/gradebooks/bulk-actions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookBulkActionResponse> bulkAction(
            @Valid @RequestBody GradebookBulkActionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(gradebookBulkActionService.execute(request, authentication.getName()));
    }

    @PostMapping("/admin/gradebooks/bulk-actions/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookBulkActionPreviewResponse> previewBulkAction(
            @Valid @RequestBody GradebookBulkActionRequest request) {
        return ResponseEntity.ok(gradebookBulkActionService.preview(request));
    }

    @GetMapping("/admin/gradebooks/bulk-actions/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookBulkOperationPageDto> bulkActionHistory(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDateTime fromTime,
            @RequestParam(required = false) LocalDateTime toTime,
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(gradebookBulkActionService.searchHistory(
                actor, action, fromTime, toTime, course, section, semester, result, page, size));
    }

    @GetMapping("/admin/gradebooks/bulk-actions/history/{operationCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookBulkOperationDto> bulkActionHistoryDetail(
            @PathVariable String operationCode) {
        return ResponseEntity.ok(gradebookBulkActionService.getHistory(operationCode));
    }

    @GetMapping("/admin/gradebooks/{gradebookId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookSummaryDto> getGradebook(@PathVariable Long gradebookId) {
        return ResponseEntity.ok(gradebookService.getGradebookSummary(gradebookId));
    }

    @GetMapping("/admin/gradebooks/{gradebookId}/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GradeAuditLogDto>> auditLogs(@PathVariable Long gradebookId) {
        return ResponseEntity.ok(gradebookService.getAuditLogs(gradebookId));
    }

    @PostMapping("/admin/gradebooks/{gradebookId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookSummaryDto> lock(@PathVariable Long gradebookId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.lock(gradebookId, authentication.getName()));
    }

    @PostMapping("/admin/gradebooks/{gradebookId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookSummaryDto> unlock(@PathVariable Long gradebookId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.unlock(gradebookId, authentication.getName()));
    }

    @PostMapping("/admin/gradebooks/{gradebookId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookSummaryDto> approve(@PathVariable Long gradebookId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.approve(gradebookId, authentication.getName()));
    }

    @PostMapping("/admin/gradebooks/{gradebookId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GradebookSummaryDto> publish(@PathVariable Long gradebookId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.publish(gradebookId, authentication.getName()));
    }

    @GetMapping("/course-sections/{sectionId}/gradebook")
    @PreAuthorize("hasRole('LECTURER') or hasRole('ADMIN')")
    public ResponseEntity<GradebookDetailDto> getSectionGradebook(
            @PathVariable Long sectionId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.getSectionGradebook(sectionId, authentication.getName()));
    }

    @GetMapping("/course-sections/{sectionId}/grade-components")
    @PreAuthorize("hasRole('LECTURER') or hasRole('ADMIN')")
    public ResponseEntity<List<GradeComponentDto>> getComponents(
            @PathVariable Long sectionId, Authentication authentication) {
        return ResponseEntity.ok(gradebookService.getComponents(sectionId, authentication.getName()));
    }

    @PutMapping("/course-sections/{sectionId}/grade-components")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GradeComponentDto>> updateComponents(
            @PathVariable Long sectionId,
            @Valid @RequestBody GradeComponentConfigRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(gradebookService.updateComponents(sectionId, request, authentication.getName()));
    }

    @GetMapping("/student/grades")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentGradeDto>> getStudentGrades(Authentication authentication) {
        return ResponseEntity.ok(gradebookService.getStudentPublishedGrades(authentication.getName()));
    }
}
