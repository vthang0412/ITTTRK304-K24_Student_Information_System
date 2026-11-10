package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Course;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import jakarta.transaction.Transactional;
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

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @GetMapping
    public ResponseEntity<List<Course>> getAll() {
        return ResponseEntity.ok(courseRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> create(@RequestBody Course course) {
        if (course.getCourseCode() == null || course.getCourseCode().isBlank()) {
            throw new BadRequestException("Mã môn học không được để trống");
        }
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new BadRequestException("Tên môn học không được để trống");
        }
        if (course.getCredits() == null || course.getCredits() < 1) {
            throw new BadRequestException("Số tín chỉ phải lớn hơn 0");
        }
        course.setCourseCode(course.getCourseCode().trim());
        course.setCourseName(course.getCourseName().trim());
        return ResponseEntity.ok(courseRepository.save(course));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> update(@PathVariable Long id, @RequestBody Course course) {
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new BadRequestException("Tên môn học không được để trống");
        }
        if (course.getCredits() == null || course.getCredits() < 1) {
            throw new BadRequestException("Số tín chỉ phải lớn hơn 0");
        }
        return courseRepository.findById(id)
                .map(existing -> {
                    existing.setCourseName(course.getCourseName().trim());
                    existing.setCredits(course.getCredits());
                    existing.setDescription(course.getDescription());
                    if (course.getStatus() != null) {
                        existing.setStatus(course.getStatus());
                    }
                    return ResponseEntity.ok(courseRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (courseSectionRepository.existsByCourseId(id)) {
            throw new BadRequestException(
                    "Không thể xóa môn học vì môn học đang được sử dụng bởi ít nhất một học phần hoặc dữ liệu liên quan.");
        }

        courseRepository.delete(course);
        return ResponseEntity.noContent().build();
    }
}
