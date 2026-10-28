package com.example.student_management.repository;

import com.example.student_management.entity.GradeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeAuditLogRepository extends JpaRepository<GradeAuditLog, Long> {
    List<GradeAuditLog> findByGradebookIdOrderByCreatedAtDesc(Long gradebookId);
}
