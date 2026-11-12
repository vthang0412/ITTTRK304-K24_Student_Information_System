package com.example.student_management.entity.academic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment_batch_operation_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentBatchOperationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "student_code", length = 50)
    private String studentCode;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_email")
    private String studentEmail;

    @Column(name = "course_code", length = 50)
    private String courseCode;

    @Column(name = "course_name")
    private String courseName;

    @Column(name = "section_code", length = 100)
    private String sectionCode;

    @Column(name = "semester_name", length = 150)
    private String semesterName;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "result_status", nullable = false, length = 20)
    private String resultStatus;

    @Column(length = 1000)
    private String message;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "schedule_configured")
    private Boolean scheduleConfigured;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
