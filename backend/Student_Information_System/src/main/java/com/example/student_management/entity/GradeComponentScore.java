package com.example.student_management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_component_scores", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"grade_component_id", "enrollment_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeComponentScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_component_id", nullable = false)
    private GradeComponent gradeComponent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal score;

    private String updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
