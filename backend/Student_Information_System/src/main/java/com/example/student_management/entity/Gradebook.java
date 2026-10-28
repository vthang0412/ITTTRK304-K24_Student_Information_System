package com.example.student_management.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "gradebooks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gradebook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "section_id", unique = true, nullable = false)
    private CourseSection courseSection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradebookStatus status;

    private LocalDateTime lockedAt;

    private String lockedBy;

    private LocalDateTime approvedAt;

    private String approvedBy;

    private LocalDateTime publishedAt;

    private String publishedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
