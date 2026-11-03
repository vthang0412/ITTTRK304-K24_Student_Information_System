package com.example.student_management.controller.grade;

import com.example.student_management.dto.grade.GradeRequest;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.User;
import com.example.student_management.entity.grade.Grade;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.auth.UserRepository;
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
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GradebookService gradebookService;

    @GetMapping("/course-sections/{id}/grades")
    @PreAuthorize("hasRole('LECTURER') or hasRole('ADMIN')")
    public ResponseEntity<List<Enrollment>> getGradesBySection(
            @PathVariable("id") Long sectionId,
            Authentication authentication) {
        CourseSection section = courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("Course section not found"));
        requireSectionAccess(section, authentication);
        return ResponseEntity.ok(enrollmentRepository.findByCourseSectionId(sectionId));
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
    public ResponseEntity<List<Enrollment>> getMyGrades(Authentication authentication) {
        return ResponseEntity.ok(gradebookService.getMyPublishedEnrollments(authentication.getName()));
    }

    private void requireSectionAccess(CourseSection section, Authentication authentication) {
        User user = currentUser(authentication);
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        Lecturer lecturer = user.getLecturer();
        if (lecturer == null || section.getLecturer() == null
                || !section.getLecturer().getId().equals(lecturer.getId())) {
            throw new ForbiddenException("You are not assigned to this course section");
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new ForbiddenException("Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
