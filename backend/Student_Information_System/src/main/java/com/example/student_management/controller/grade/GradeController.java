package com.example.student_management.controller.grade;

import com.example.student_management.dto.grade.GradeRequest;
import com.example.student_management.dto.grade.GradebookEnrollmentDto;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.grade.Grade;
import com.example.student_management.service.grade.GradebookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GradeController {
    @Autowired
    private GradebookService gradebookService;

    @GetMapping("/course-sections/{id}/grades")
    @PreAuthorize("hasRole('LECTURER') or hasRole('ADMIN')")
    public ResponseEntity<List<GradebookEnrollmentDto>> getGradesBySection(
            @PathVariable("id") Long sectionId,
            Authentication authentication) {
        return ResponseEntity.ok(
                gradebookService.getSectionGradebook(sectionId, authentication.getName()).getEnrollments());
    }

    @PutMapping("/enrollments/{id}/grade")
    @PreAuthorize("hasRole('LECTURER') or hasRole('ADMIN')")
    public ResponseEntity<Grade> updateGrade(
            @PathVariable Long id,
            @Valid @RequestBody GradeRequest gradeRequest,
            Authentication authentication) {
        return ResponseEntity.ok(gradebookService.saveGrade(id, gradeRequest, authentication.getName()));
    }

    @GetMapping("/grades/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentGradeDto>> getMyGrades(Authentication authentication) {
        return ResponseEntity.ok(gradebookService.getStudentPublishedGrades(authentication.getName()));
    }
}
