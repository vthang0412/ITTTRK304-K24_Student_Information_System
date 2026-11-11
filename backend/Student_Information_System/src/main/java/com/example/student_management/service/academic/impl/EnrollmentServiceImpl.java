package com.example.student_management.service.academic.impl;

import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.academic.EnrollmentService;
import com.example.student_management.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private CourseSectionRepository courseSectionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;

    @Override
    public List<Enrollment> getMyEnrollments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            return List.of();
        }
        return enrollmentRepository.findByStudentId(user.getStudent().getId());
    }

    @Override
    @Transactional
    public Enrollment enroll(String username, Long sectionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            throw new BadRequestException("Student profile not found");
        }
        Student student = user.getStudent();
        if (student.getStatus() == AccountStatus.INACTIVE) {
            throw new ForbiddenException("Account is disabled");
        }
        CourseSection section = courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException("Course section not found"));
        if (section.getStatus() != CourseSectionStatus.OPEN) {
            throw new BadRequestException("Course section is not open for registration");
        }
        Semester semester = section.getSemester();
        if (semester != null && semester.getStatus() == SemesterStatus.COMPLETED) {
            throw new BadRequestException("Học kỳ này đã kết thúc, không thể đăng ký học phần");
        }
        LocalDateTime now = LocalDateTime.now();
        int capacity = section.getCapacity() != null ? section.getCapacity() : 40;
        long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(sectionId, EnrollmentStatus.ENROLLED);
        if (enrolledCount >= capacity) {
            throw new ConflictException("Course section is full");
        }
        Enrollment saved = enrollmentRepository.findByStudentIdAndCourseSectionId(student.getId(), sectionId)
                .map(existing -> {
                    if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
                        throw new ConflictException("Already enrolled in this section");
                    }
                    existing.setStatus(EnrollmentStatus.ENROLLED);
                    existing.setRegisteredAt(now);
                    existing.setWithdrawnAt(null);
                    return enrollmentRepository.save(existing);
                })
                .orElseGet(() -> enrollmentRepository.save(Enrollment.builder()
                        .student(student)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .registeredAt(now)
                        .build()));

        // Tự động gửi thông báo đăng ký thành công kèm Thời khóa biểu và Ngày đăng ký
        try {
            Course course = section.getCourse();
            String courseName = course != null ? course.getCourseName() : "Học phần";
            int credits = course != null && course.getCredits() != null ? course.getCredits() : 0;
            String schedule = (section.getSchedule() != null && !section.getSchedule().isBlank())
                    ? section.getSchedule()
                    : "Chưa cập nhật";
            String lecturerName = (section.getLecturer() != null && section.getLecturer().getFullName() != null)
                    ? section.getLecturer().getFullName()
                    : "Chưa phân công";
            String dateFormatted = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            String content = "Bạn đã đăng ký thành công lớp " + section.getSectionCode() + " - " + courseName + " ("
                    + credits + " tín chỉ).\n"
                    + "• Giảng viên: " + lecturerName + "\n"
                    + "• Thời khóa biểu: " + schedule + "\n"
                    + "• Ngày đăng ký: " + dateFormatted + "\n"
                    + "Học phần đã được đồng bộ vào Thời khóa biểu và Lịch học của bạn.";

            notificationService.sendSystemNotification(
                    user,
                    "Đăng ký học phần thành công: " + section.getSectionCode(),
                    content,
                    "ENROLLMENT");
        } catch (Exception ignored) {
        }

        return saved;
    }

    @Override
    @Transactional
    public Enrollment withdraw(String username, Long enrollmentId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            throw new BadRequestException("Student profile not found");
        }
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        if (!enrollment.getStudent().getId().equals(user.getStudent().getId())) {
            throw new ForbiddenException("Not allowed");
        }
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
            throw new BadRequestException("Already withdrawn");
        }
        LocalDateTime now = LocalDateTime.now();
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(now);
        Enrollment saved = enrollmentRepository.save(enrollment);

        // Tự động gửi thông báo huỷ đăng ký học phần
        try {
            CourseSection section = enrollment.getCourseSection();
            String sectionCode = section != null ? section.getSectionCode() : "";
            String courseName = (section != null && section.getCourse() != null) ? section.getCourse().getCourseName()
                    : "Học phần";
            String dateFormatted = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            String content = "Bạn đã huỷ đăng ký lớp học phần " + sectionCode + " - " + courseName + " vào ngày "
                    + dateFormatted + ".";

            notificationService.sendSystemNotification(
                    user,
                    "Huỷ đăng ký học phần: " + sectionCode,
                    content,
                    "ENROLLMENT");
        } catch (Exception ignored) {
        }

        return saved;
    }
}
