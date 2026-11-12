package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIStudyPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIStudyPlanItemRepository extends JpaRepository<AIStudyPlanItem, Long> {

    List<AIStudyPlanItem> findByStudyPlanIdOrderByStudyDateAscIdAsc(Long planId);

    Optional<AIStudyPlanItem> findByIdAndStudyPlanId(Long id, Long planId);
}

