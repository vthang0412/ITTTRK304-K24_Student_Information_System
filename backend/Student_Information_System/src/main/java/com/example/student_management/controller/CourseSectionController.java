package com.example.student_management.controller;

import com.example.student_management.entity.CourseSection;
import com.example.student_management.entity.Lecturer;
import com.example.student_management.entity.User;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.CourseSectionRepository;
import com.example.student_management.repository.LecturerRepository;
import com.example.student_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course-sections")
public class CourseSectionController {
    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @GetMapping
    public ResponseEntity<List<CourseSection>> getAll() {
        return ResponseEntity.ok(courseSectionRepository.findAll());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<CourseSection>> getMine(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
        Lecturer lecturer = user.getLecturer();
        if (lecturer == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(courseSectionRepository.findByLecturerId(lecturer.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseSection> create(@RequestBody CourseSection courseSection) {
        return ResponseEntity.ok(courseSectionRepository.save(courseSection));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseSection> update(@PathVariable Long id, @RequestBody CourseSection courseSection) {
        return courseSectionRepository.findById(id)
                .map(existing -> {
                    existing.setCourse(courseSection.getCourse());
                    existing.setSemester(courseSection.getSemester());
                    existing.setLecturer(courseSection.getLecturer());
                    existing.setCapacity(courseSection.getCapacity());
                    existing.setSchedule(courseSection.getSchedule());
                    existing.setStatus(courseSection.getStatus());
                    return ResponseEntity.ok(courseSectionRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
