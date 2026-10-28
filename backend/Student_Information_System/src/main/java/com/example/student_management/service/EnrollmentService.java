package com.example.student_management.service;



import com.example.student_management.entity.Enrollment;
import java.util.List;

public interface EnrollmentService {
    List<Enrollment> getMyEnrollments(String username);
    Enrollment enroll(String username, Long sectionId);
    Enrollment withdraw(String username, Long enrollmentId);
}
