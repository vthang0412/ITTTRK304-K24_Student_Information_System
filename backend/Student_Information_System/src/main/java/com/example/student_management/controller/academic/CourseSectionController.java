package com.example.student_management.controller.academic;

import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionSchedule;
import com.example.student_management.entity.academic.CourseSectionStatus;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.academic.SemesterStatus;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/course-sections")
public class CourseSectionController {
    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private CourseSectionScheduleRepository courseSectionScheduleRepository;

    @Autowired
    private RecurringCourseScheduleRepository recurringCourseScheduleRepository;

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
        List<CourseSection> sections = courseSectionRepository.findAll();
        if (sections.isEmpty()) {
            return ResponseEntity.ok(sections);
        }

        Set<Long> sectionIds = sections.stream().map(CourseSection::getId).collect(Collectors.toSet());
        Map<Long, Long> registeredCounts = enrollmentRepository.countActiveByCourseSectionIds(
                        sectionIds, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED))
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Set<Long> sectionsWithEnrollments = new java.util.HashSet<>(
                enrollmentRepository.findCourseSectionIdsWithEnrollmentHistory());
        Set<Long> sectionsWithGradebooks = new java.util.HashSet<>(
                gradebookRepository.findCourseSectionIdsWithGradebooks());
        Set<Long> sectionsWithScheduleHistory = new java.util.HashSet<>(
                courseSectionScheduleRepository.findCourseSectionIdsWithScheduleHistory());
        sectionsWithScheduleHistory.addAll(recurringCourseScheduleRepository.findCourseSectionIdsWithScheduleHistory());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        sections.forEach(section -> {
            section.setRegisteredCount(registeredCounts.getOrDefault(section.getId(), 0L));
            section.setDeleteBlockedReason(deleteBlockedReason(
                    section,
                    sectionsWithEnrollments.contains(section.getId()),
                    sectionsWithGradebooks.contains(section.getId()),
                    sectionsWithScheduleHistory.contains(section.getId()),
                    today,
                    now));
        });
        return ResponseEntity.ok(sections);
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
    @Transactional
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
        if (saved.getSchedule() != null && !saved.getSchedule().isBlank()) {
            courseSectionScheduleRepository.save(CourseSectionSchedule.builder()
                    .courseSection(saved)
                    .schedule(saved.getSchedule())
                    .effectiveFrom(semester.getStartDate())
                    .effectiveTo(semester.getEndDate())
                    .build());
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
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
                        boolean hasRecurringSchedule = !recurringCourseScheduleRepository
                                .findByCourseSectionIdAndCancelledFromIsNullOrderByDayOfWeekAscStartTimeAsc(id)
                                .isEmpty();
                        if (hasRecurringSchedule && !Objects.equals(existing.getSchedule(), courseSection.getSchedule())) {
                            throw new BadRequestException(
                                    "Lớp đang dùng lịch định kỳ. Hãy chỉnh sửa lịch trong chức năng Thiết lập lịch.");
                        }
                        updateSchedule(existing, courseSection.getSchedule(), courseSection.getScheduleEffectiveFrom());
                    }
                    if (courseSection.getStatus() != null) {
                        existing.setStatus(courseSection.getStatus());
                    }
                    return ResponseEntity.ok(courseSectionRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void updateSchedule(CourseSection section, String newSchedule, LocalDate requestedEffectiveFrom) {
        if (Objects.equals(section.getSchedule(), newSchedule)) {
            return;
        }

        Semester semester = section.getSemester();
        if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
            throw new BadRequestException("Không thể lưu lịch vì học kỳ chưa có ngày bắt đầu/kết thúc");
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate effectiveFrom = requestedEffectiveFrom != null ? requestedEffectiveFrom : today;
        if (effectiveFrom.isBefore(today)) {
            throw new BadRequestException("Ngày áp dụng lịch mới không được nằm trong quá khứ");
        }
        if (effectiveFrom.isBefore(semester.getStartDate())) {
            effectiveFrom = semester.getStartDate();
        }
        if (effectiveFrom.isAfter(semester.getEndDate())) {
            throw new BadRequestException("Ngày áp dụng lịch mới phải nằm trong thời gian học kỳ");
        }

        List<CourseSectionSchedule> history =
                courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(section.getId());
        if (history.isEmpty() && section.getSchedule() != null && !section.getSchedule().isBlank()
                && effectiveFrom.isAfter(semester.getStartDate())) {
            courseSectionScheduleRepository.save(CourseSectionSchedule.builder()
                    .courseSection(section)
                    .schedule(section.getSchedule())
                    .effectiveFrom(semester.getStartDate())
                    .effectiveTo(effectiveFrom.minusDays(1))
                    .build());
        }

        for (CourseSectionSchedule entry : history) {
            if (entry.getEffectiveFrom().isBefore(effectiveFrom)
                    && (entry.getEffectiveTo() == null || !entry.getEffectiveTo().isBefore(effectiveFrom))) {
                entry.setEffectiveTo(effectiveFrom.minusDays(1));
                courseSectionScheduleRepository.save(entry);
            } else if (!entry.getEffectiveFrom().isBefore(effectiveFrom)) {
                if (entry.getCancelledFrom() == null || entry.getCancelledFrom().isAfter(effectiveFrom)) {
                    entry.setCancelledFrom(effectiveFrom);
                    courseSectionScheduleRepository.save(entry);
                }
            }
        }

        courseSectionScheduleRepository.save(CourseSectionSchedule.builder()
                .courseSection(section)
                .schedule(newSchedule)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(semester.getEndDate())
                .build());
        section.setSchedule(newSchedule);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        CourseSection section = courseSectionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Course section not found"));

        String blockedReason = deleteBlockedReason(
                section,
                enrollmentRepository.existsByCourseSectionId(id),
                gradebookRepository.findByCourseSectionId(id).isPresent(),
                courseSectionScheduleRepository.existsByCourseSectionId(id)
                        || recurringCourseScheduleRepository.existsByCourseSectionId(id),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        if (blockedReason != null) {
            throw new BadRequestException("Không thể xóa lớp học phần. " + blockedReason);
        }

        courseSectionRepository.delete(section);
        return ResponseEntity.noContent().build();
    }

    private String deleteBlockedReason(CourseSection section,
                                       boolean hasEnrollmentHistory,
                                       boolean hasGradebook,
                                       boolean hasScheduleHistory,
                                       LocalDate today,
                                       LocalDateTime now) {
        if (hasEnrollmentHistory) {
            return "Lớp đã có đăng ký hoặc lịch sử ghi danh của sinh viên cần được bảo toàn.";
        }
        if (section.getLecturer() != null) {
            return "Lớp học phần đang được phân công giảng viên.";
        }
        Semester semester = section.getSemester();
        if (section.getStatus() == CourseSectionStatus.OPEN
                || (semester != null && (semester.getStatus() == SemesterStatus.REGISTRATION_OPEN
                || (semester.getRegistrationStart() != null && semester.getRegistrationEnd() != null
                && !now.isBefore(semester.getRegistrationStart())
                && !now.isAfter(semester.getRegistrationEnd()))))) {
            return "Lớp học phần đang mở đăng ký.";
        }
        if (section.getStatus() == CourseSectionStatus.IN_PROGRESS
                || (semester != null && (semester.getStatus() == SemesterStatus.IN_PROGRESS
                || (semester.getStartDate() != null && semester.getEndDate() != null
                && !today.isBefore(semester.getStartDate()) && !today.isAfter(semester.getEndDate()))))) {
            return "Lớp học phần đang diễn ra.";
        }
        if (hasGradebook) {
            return "Lớp đã có bảng điểm hoặc lịch sử điểm cần được bảo toàn.";
        }
        if (hasScheduleHistory || (section.getSchedule() != null && !section.getSchedule().isBlank())) {
            return "Lớp đã có lịch học hoặc lịch sử thay đổi lịch cần được bảo toàn.";
        }
        return null;
    }
}
