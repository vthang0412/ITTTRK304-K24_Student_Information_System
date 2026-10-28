package com.example.student_management.repository;



import com.example.student_management.entity.Enrollment;
import com.example.student_management.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    List<Enrollment> findByCourseSectionId(Long sectionId);
    Optional<Enrollment> findByStudentIdAndCourseSectionId(Long studentId, Long sectionId);
    long countByCourseSectionIdAndStatus(Long sectionId, EnrollmentStatus status);
}
