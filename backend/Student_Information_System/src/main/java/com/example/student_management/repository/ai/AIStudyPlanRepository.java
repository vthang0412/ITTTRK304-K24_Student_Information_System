package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIStudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIStudyPlanRepository extends JpaRepository<AIStudyPlan, Long> {

    List<AIStudyPlan> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Optional<AIStudyPlan> findByIdAndStudentId(Long id, Long studentId);

    long countByStudentIdAndStatus(Long studentId, String status);
}

