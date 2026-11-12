package com.example.student_management.repository.ai;

import com.example.student_management.entity.ai.AIUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AIUsageLogRepository extends JpaRepository<AIUsageLog, Long> {
    long countByStudentIdAndCreatedAtAfter(Long studentId, LocalDateTime since);
}

