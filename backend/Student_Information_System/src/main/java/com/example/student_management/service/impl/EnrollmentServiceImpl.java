package com.example.student_management.service.impl;



import com.example.student_management.entity.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.*;
import com.example.student_management.service.EnrollmentService;
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
        if (semester == null || semester.getStatus() != SemesterStatus.REGISTRATION_OPEN) {
            throw new BadRequestException("Registration is not open for this semester");
        }
        LocalDateTime now = LocalDateTime.now();
        if (semester.getRegistrationStart() != null && now.isBefore(semester.getRegistrationStart())) {
            throw new BadRequestException("Registration period has not started");
        }
        if (semester.getRegistrationEnd() != null && now.isAfter(semester.getRegistrationEnd())) {
            throw new BadRequestException("Registration period has ended");
        }
        long enrolledCount = enrollmentRepository.countByCourseSectionIdAndStatus(sectionId, EnrollmentStatus.ENROLLED);
        if (enrolledCount >= section.getCapacity()) {
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
