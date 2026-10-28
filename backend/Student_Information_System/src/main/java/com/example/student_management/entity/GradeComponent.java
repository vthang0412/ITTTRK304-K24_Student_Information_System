package com.example.student_management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_components", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"gradebook_id", "component_name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gradebook_id", nullable = false)
    private Gradebook gradebook;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal maxScore;

    private String componentType;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(name = "is_required", nullable = false)
    private Boolean required;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
