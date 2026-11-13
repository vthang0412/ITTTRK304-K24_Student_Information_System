package com.example.student_management.repository.academic;

import com.example.student_management.entity.academic.EnrollmentBatchOperationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentBatchOperationItemRepository extends JpaRepository<EnrollmentBatchOperationItem, Long> {
    List<EnrollmentBatchOperationItem> findByBatchIdOrderByIdAsc(Long batchId);
}
