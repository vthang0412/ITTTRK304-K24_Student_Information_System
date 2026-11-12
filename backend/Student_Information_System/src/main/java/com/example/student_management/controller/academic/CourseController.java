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
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @GetMapping
    public ResponseEntity<List<Course>> getAll() {
        List<Course> courses = courseRepository.findAll();
        Set<Long> linkedCourseIds = courseSectionRepository.findCourseIdsWithSections()
                .stream().collect(Collectors.toSet());
        courses.forEach(course -> {
            if (linkedCourseIds.contains(course.getId())) {
                course.setDeleteBlockedReason(
                        "Môn học đang được lớp học phần tham chiếu; cần giữ lại đăng ký, điểm và lịch sử lịch học liên quan.");
            }
        });
        return ResponseEntity.ok(courses);
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (courseSectionRepository.existsByCourseId(id)) {
            throw new BadRequestException("Không thể xóa môn học vì môn học đang được lớp học phần tham chiếu. "
                    + "Các đăng ký, bảng điểm và lịch sử lịch học liên quan cần được bảo toàn.");
        }

        courseRepository.delete(course);
        return ResponseEntity.noContent().build();
    }
}
