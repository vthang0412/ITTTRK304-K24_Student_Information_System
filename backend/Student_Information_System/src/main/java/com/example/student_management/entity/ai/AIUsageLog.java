package com.example.student_management.entity.ai;

import com.example.student_management.entity.auth.Student;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "feature_type", nullable = false, length = 50)
    private String featureType; // CHAT / INSIGHTS / ADVISOR / PLANNER

    @Column(nullable = false, length = 50)
    private String provider; // GEMINI / OPENAI / INTERNAL

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS / FAILED

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

