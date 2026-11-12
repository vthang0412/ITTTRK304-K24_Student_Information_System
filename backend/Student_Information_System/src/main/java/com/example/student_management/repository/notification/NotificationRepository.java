package com.example.student_management.repository.notification;

import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :userId OR (n.recipient IS NULL AND (n.targetRole IS NULL OR n.targetRole = :role)) ORDER BY n.createdAt DESC")
    List<Notification> findNotificationsForUser(@Param("userId") Long userId, @Param("role") Role role);
}
