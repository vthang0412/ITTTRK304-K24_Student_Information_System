package com.example.student_management.controller;



import com.example.student_management.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalStudents", studentRepository.count());
        result.put("totalLecturers", lecturerRepository.count());
        result.put("totalCourses", courseRepository.count());
        result.put("totalCourseSections", courseSectionRepository.count());
        return ResponseEntity.ok(result);
    }
}
