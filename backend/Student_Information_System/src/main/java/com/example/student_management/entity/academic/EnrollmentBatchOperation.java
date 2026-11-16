package com.example.student_management.entity.academic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment_batch_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentBatchOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 36)
    private String batchCode;

    @Column(name = "admin_username", nullable = false, length = 100)
    private String adminUsername;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "semester_id")
    private Long semesterId;

    @Column(name = "semester_name", length = 150)
    private String semesterName;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
