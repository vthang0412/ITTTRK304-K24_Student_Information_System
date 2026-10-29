package com.example.student_management.service;

import com.example.student_management.dto.NotificationDto;
import com.example.student_management.dto.request.NotificationRequest;
import com.example.student_management.entity.User;

import java.util.List;

public interface NotificationService {
    List<NotificationDto> getMyNotifications(String username);

    long getUnreadCount(String username);

    NotificationDto markAsRead(Long id, String username);

    void markAllAsRead(String username);

    NotificationDto createNotification(NotificationRequest request, String senderUsername);

    void deleteNotification(Long id, String username);

    void sendSystemNotification(User recipient, String title, String content, String type);
}

