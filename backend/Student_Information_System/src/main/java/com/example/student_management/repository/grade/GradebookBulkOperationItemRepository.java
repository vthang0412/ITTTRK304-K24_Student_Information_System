package com.example.student_management.repository.grade;

import com.example.student_management.entity.grade.GradebookBulkOperationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradebookBulkOperationItemRepository extends JpaRepository<GradebookBulkOperationItem, Long> {
    List<GradebookBulkOperationItem> findByOperationIdOrderByIdAsc(Long operationId);
}
