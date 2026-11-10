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
import com.example.student_management.entity.grade.GradebookStatus;
import com.example.student_management.repository.grade.GradebookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private GradebookRepository gradebookRepository;

    @Override
    public List<Enrollment> getMyEnrollments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getStudent() == null) {
            return List.of();
        }
        List<Enrollment> list = enrollmentRepository.findByStudentId(user.getStudent().getId());
        // Bảo mật H-02: Sinh viên chỉ được thấy điểm khi sổ điểm của lớp đã ở trạng thái PUBLISHED
        for (Enrollment enrollment : list) {
            if (enrollment.getGrade() != null) {
                CourseSection section = enrollment.getCourseSection();
                boolean published = false;
                if (section != null) {
                    published = gradebookRepository.findByCourseSectionId(section.getId())
                            .map(gb -> gb.getStatus() == GradebookStatus.PUBLISHED)
                            .orElse(false);
                }
                if (!published) {
                    enrollment.setGrade(null);
                }
            }
        }
        return list;
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
        CourseSection section = courseSectionRepository.findByIdForUpdate(sectionId)
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
        return enrollmentRepository.findByStudentIdAndCourseSectionId(student.getId(), sectionId)
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
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(LocalDateTime.now());
        return enrollmentRepository.save(enrollment);
    }
}
