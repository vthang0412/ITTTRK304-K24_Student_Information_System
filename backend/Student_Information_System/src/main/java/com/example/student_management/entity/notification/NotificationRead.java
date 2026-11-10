package com.example.student_management.entity.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_reads", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "notification_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "read_at", nullable = false)
    @Builder.Default
    private LocalDateTime readAt = LocalDateTime.now();
}

