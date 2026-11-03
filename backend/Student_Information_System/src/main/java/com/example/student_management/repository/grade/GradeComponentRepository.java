package com.example.student_management.repository.grade;

import com.example.student_management.entity.grade.GradeComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeComponentRepository extends JpaRepository<GradeComponent, Long> {
    List<GradeComponent> findByGradebookIdOrderByDisplayOrderAscIdAsc(Long gradebookId);

    Optional<GradeComponent> findByIdAndGradebookId(Long id, Long gradebookId);

    long countByGradebookId(Long gradebookId);
}
