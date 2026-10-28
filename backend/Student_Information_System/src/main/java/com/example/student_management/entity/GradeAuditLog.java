package com.example.student_management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "grade_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gradebook_id", nullable = false)
    private Gradebook gradebook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_component_id")
    private GradeComponent gradeComponent;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 1000)
    private String oldValue;

    @Column(length = 1000)
    private String newValue;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
