package com.example.student_management.repository.notification;

import com.example.student_management.entity.notification.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    boolean existsByUserIdAndNotificationId(Long userId, Long notificationId);

    @Query("SELECT nr.notificationId FROM NotificationRead nr WHERE nr.userId = :userId")
    Set<Long> findReadNotificationIdsByUserId(@Param("userId") Long userId);
}

