package com.example.student_management.repository.notification;

import com.example.student_management.entity.notification.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    @Query("SELECT r.notification.id FROM NotificationRead r WHERE r.user.id = :userId AND r.notification.id IN :notificationIds")
    List<Long> findReadNotificationIdsByUserIdAndNotificationIds(@Param("userId") Long userId,
                                                                 @Param("notificationIds") List<Long> notificationIds);
}
