package com.example.student_management.repository.grade;

import com.example.student_management.entity.grade.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findByEnrollmentId(Long enrollmentId);

    List<Grade> findByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
