package com.example.student_management.repository;

import com.example.student_management.entity.GradeComponentScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeComponentScoreRepository extends JpaRepository<GradeComponentScore, Long> {
    List<GradeComponentScore> findByEnrollmentId(Long enrollmentId);

    List<GradeComponentScore> findByGradeComponentGradebookId(Long gradebookId);

    Optional<GradeComponentScore> findByGradeComponentIdAndEnrollmentId(Long gradeComponentId, Long enrollmentId);

    long countByGradeComponentGradebookId(Long gradebookId);
}
