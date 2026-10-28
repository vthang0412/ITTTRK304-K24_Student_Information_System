package com.example.student_management.controller;

import com.example.student_management.dto.*;
import com.example.student_management.dto.request.GradeComponentConfigRequest;
import com.example.student_management.service.GradebookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GradebookController {
    @Autowired
    private GradebookService gradebookService;

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
