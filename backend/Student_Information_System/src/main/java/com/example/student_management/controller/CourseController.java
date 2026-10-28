package com.example.student_management.controller;



import com.example.student_management.entity.Course;
import com.example.student_management.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    @Autowired
    private CourseRepository courseRepository;

    @GetMapping
    public ResponseEntity<List<Course>> getAll() {
        return ResponseEntity.ok(courseRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> create(@RequestBody Course course) {
        return ResponseEntity.ok(courseRepository.save(course));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> update(@PathVariable Long id, @RequestBody Course course) {
        return courseRepository.findById(id)
                .map(existing -> {
                    existing.setCourseName(course.getCourseName());
                    existing.setCredits(course.getCredits());
                    existing.setDescription(course.getDescription());
                    existing.setStatus(course.getStatus());
                    return ResponseEntity.ok(courseRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
