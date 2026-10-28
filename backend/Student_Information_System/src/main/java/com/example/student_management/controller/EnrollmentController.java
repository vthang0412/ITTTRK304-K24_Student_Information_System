package com.example.student_management.controller;



import com.example.student_management.entity.*;
import com.example.student_management.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EnrollmentController {
    @Autowired
    private EnrollmentService enrollmentService;

    @GetMapping("/enrollments/me")
    public ResponseEntity<List<Enrollment>> getMyEnrollments(Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(authentication.getName()));
    }

    @PostMapping("/course-sections/{id}/enroll")
    public ResponseEntity<Enrollment> enroll(@PathVariable("id") Long sectionId, Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.enroll(authentication.getName(), sectionId));
    }

    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Enrollment> withdraw(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(enrollmentService.withdraw(authentication.getName(), id));
    }
}
