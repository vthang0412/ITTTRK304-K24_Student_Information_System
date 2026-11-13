package com.example.student_management.entity.grade;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "gradebook_bulk_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradebookBulkOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_code", nullable = false, unique = true, length = 36)
    private String operationCode;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
