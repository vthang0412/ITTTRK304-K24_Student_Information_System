package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Course;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
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
            throw new BadRequestException("Mã môn học là bắt buộc");
        }
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new BadRequestException("Tên môn học là bắt buộc");
        }
        if (course.getCredits() == null || course.getCredits() < 1) {
            throw new BadRequestException("Số tín chỉ phải lớn hơn 0");
        }
        String code = course.getCourseCode().trim();
        if (courseRepository.existsByCourseCode(code)) {
            throw new ConflictException("Mã môn học đã tồn tại");
        }
        course.setCourseCode(code);
        return ResponseEntity.ok(courseRepository.save(course));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> update(@PathVariable Long id, @RequestBody Course course) {
        return courseRepository.findById(id)
                .map(existing -> {
                    if (course.getCourseName() != null && !course.getCourseName().isBlank()) {
                        existing.setCourseName(course.getCourseName().trim());
                    }
                    if (course.getCredits() != null && course.getCredits() > 0) {
                        existing.setCredits(course.getCredits());
                    }
                    if (course.getDescription() != null) {
                        existing.setDescription(course.getDescription());
                    }
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
