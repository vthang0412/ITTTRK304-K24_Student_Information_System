package com.example.student_management.service.academic;

import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;

import java.util.List;

public interface EnrollmentService {
    List<Enrollment> getMyEnrollments(String username);

    List<Enrollment> getPendingEnrollments();

    List<Enrollment> getEnrollmentsForAdmin(EnrollmentStatus status);

    Enrollment enroll(String username, Long sectionId);

    Enrollment approve(String adminUsername, Long enrollmentId);

    void validateApproval(Long enrollmentId);

    Enrollment reject(String adminUsername, Long enrollmentId, String reason);

    Enrollment withdraw(String username, Long enrollmentId);
}
