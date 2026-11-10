package com.example.student_management.entity.grade;


import com.example.student_management.entity.academic.Enrollment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne
    @JoinColumn(name = "enrollment_id", unique = true, nullable = false)
    private Enrollment enrollment;

    @Column(precision = 5, scale = 2)
    private BigDecimal componentScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal finalScore;

    private String updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
