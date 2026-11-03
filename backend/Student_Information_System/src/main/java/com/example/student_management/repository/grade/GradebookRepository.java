package com.example.student_management.repository.grade;



import com.example.student_management.entity.grade.Gradebook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GradebookRepository extends JpaRepository<Gradebook, Long> {
    Optional<Gradebook> findByCourseSectionId(Long sectionId);
}
