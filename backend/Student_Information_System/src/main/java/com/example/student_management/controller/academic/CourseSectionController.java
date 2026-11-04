package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionStatus;
import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import com.example.student_management.repository.auth.LecturerRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.grade.GradebookRepository;
import jakarta.transaction.Transactional;
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
    private CourseRepository courseRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private GradebookRepository gradebookRepository;

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
        if (courseSection.getSectionCode() == null || courseSection.getSectionCode().isBlank()) {
            throw new BadRequestException("Mã lớp học phần là bắt buộc");
        }
        if (courseSectionRepository.existsBySectionCode(courseSection.getSectionCode().trim())) {
            throw new ConflictException("Mã lớp học phần đã tồn tại");
        }
        courseSection.setSectionCode(courseSection.getSectionCode().trim());

        if (courseSection.getCourse() == null || courseSection.getCourse().getId() == null) {
            throw new BadRequestException("Môn học là bắt buộc");
        }
        Course course = courseRepository.findById(courseSection.getCourse().getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy môn học"));
        courseSection.setCourse(course);

        if (courseSection.getSemester() == null || courseSection.getSemester().getId() == null) {
            throw new BadRequestException("Học kỳ là bắt buộc");
        }
        Semester semester = semesterRepository.findById(courseSection.getSemester().getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy học kỳ"));
        courseSection.setSemester(semester);

        if (courseSection.getLecturer() != null && courseSection.getLecturer().getId() != null) {
            Lecturer lecturer = lecturerRepository.findById(courseSection.getLecturer().getId()).orElse(null);
            courseSection.setLecturer(lecturer);
        } else {
            courseSection.setLecturer(null);
        }

        if (courseSection.getCapacity() == null || courseSection.getCapacity() < 1) {
            courseSection.setCapacity(40);
        }

        if (courseSection.getStatus() == null) {
            courseSection.setStatus(CourseSectionStatus.OPEN);
        }

        CourseSection saved = courseSectionRepository.save(courseSection);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourseSection> update(@PathVariable Long id, @RequestBody CourseSection courseSection) {
        return courseSectionRepository.findById(id)
                .map(existing -> {
                    if (courseSection.getCourse() != null && courseSection.getCourse().getId() != null) {
                        courseRepository.findById(courseSection.getCourse().getId()).ifPresent(existing::setCourse);
                    }
                    if (courseSection.getSemester() != null && courseSection.getSemester().getId() != null) {
                        semesterRepository.findById(courseSection.getSemester().getId()).ifPresent(existing::setSemester);
                    }
                    if (courseSection.getLecturer() != null && courseSection.getLecturer().getId() != null) {
                        lecturerRepository.findById(courseSection.getLecturer().getId()).ifPresent(existing::setLecturer);
                    } else if (courseSection.getLecturer() == null) {
                        existing.setLecturer(null);
                    }
                    if (courseSection.getCapacity() != null && courseSection.getCapacity() >= 1) {
                        existing.setCapacity(courseSection.getCapacity());
                    }
                    if (courseSection.getSchedule() != null) {
                        existing.setSchedule(courseSection.getSchedule());
                    }
                    if (courseSection.getStatus() != null) {
                        existing.setStatus(courseSection.getStatus());
                    }
                    return ResponseEntity.ok(courseSectionRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        CourseSection section = courseSectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course section not found"));

        if (enrollmentRepository.existsByCourseSectionId(id)) {
            throw new BadRequestException(
                    "Không thể xóa học phần vì đã có sinh viên đăng ký hoặc dữ liệu ghi danh cần được giữ lại.");
        }

        if (gradebookRepository.findByCourseSectionId(id).isPresent()) {
            throw new BadRequestException("Không thể xóa học phần vì đã có dữ liệu điểm hoặc sổ điểm liên quan.");
        }

        if (section.getStatus() == CourseSectionStatus.OPEN || section.getStatus() == CourseSectionStatus.IN_PROGRESS
                || section.getStatus() == CourseSectionStatus.CLOSED) {
            throw new BadRequestException("Không thể xóa học phần vì học phần đang còn hoạt động hoặc đang diễn ra.");
        }

        courseSectionRepository.delete(section);
        return ResponseEntity.noContent().build();
    }
}
