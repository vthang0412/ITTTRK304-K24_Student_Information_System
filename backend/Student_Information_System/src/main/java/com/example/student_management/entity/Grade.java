package com.example.student_management.entity;



import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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

    @Column(precision = 4, scale = 2)
    private BigDecimal componentScore;

    @Column(precision = 4, scale = 2)
    private BigDecimal finalScore;

    private String updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
