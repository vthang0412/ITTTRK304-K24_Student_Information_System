package com.example.student_management.entity.grade;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "gradebook_bulk_operation_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradebookBulkOperationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "gradebook_id")
    private Long gradebookId;

    @Column(name = "course_code", length = 100)
    private String courseCode;

    @Column(name = "course_name")
    private String courseName;

    @Column(name = "section_code", length = 100)
    private String sectionCode;

    @Column(name = "semester_name")
    private String semesterName;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(length = 30)
    private String status;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "result_status", nullable = false, length = 20)
    private String resultStatus;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
