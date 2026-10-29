package com.example.student_management.service.impl;

import com.example.student_management.dto.NotificationDto;
import com.example.student_management.dto.request.NotificationRequest;
import com.example.student_management.entity.Notification;
import com.example.student_management.entity.Role;
import com.example.student_management.entity.User;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.NotificationRepository;
import com.example.student_management.repository.UserRepository;
import com.example.student_management.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(String username) {
        User user = currentUser(username);
        return notificationRepository.findNotificationsForUser(user.getId(), user.getRole())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = currentUser(username);
        return notificationRepository.countUnreadForUser(user.getId(), user.getRole());
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(Long id, String username) {
        User user = currentUser(username);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        // Kiểm tra quyền truy cập thông báo
        boolean isRecipient = notification.getRecipient() != null && notification.getRecipient().getId().equals(user.getId());
        boolean isRoleTargeted = notification.getRecipient() == null && (notification.getTargetRole() == null || notification.getTargetRole() == user.getRole());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isRecipient && !isRoleTargeted && !isAdmin) {
            throw new ForbiddenException("You cannot access this notification");
        }

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        User user = currentUser(username);
        notificationRepository.markAllAsReadForUser(user.getId());
    }

    @Override
    @Transactional
    public NotificationDto createNotification(NotificationRequest request, String senderUsername) {
        User sender = currentUser(senderUsername);

        User recipient = null;
        if (request.getRecipientId() != null) {
            recipient = userRepository.findById(request.getRecipientId())
                    .orElseThrow(() -> new NotFoundException("Recipient user not found"));
        }

        Role targetRole = null;
        if (request.getTargetRole() != null && !request.getTargetRole().isBlank()) {
            try {
                targetRole = Role.valueOf(request.getTargetRole().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                targetRole = null;
            }
        }

        String type = (request.getType() != null && !request.getType().isBlank())
                ? request.getType().trim().toUpperCase()
                : "SYSTEM";

        Notification notification = Notification.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .type(type)
                .recipient(recipient)
                .sender(sender)
                .targetRole(targetRole)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, String username) {
        User user = currentUser(username);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (user.getRole() != Role.ADMIN &&
                (notification.getRecipient() == null || !notification.getRecipient().getId().equals(user.getId()))) {
            throw new ForbiddenException("You do not have permission to delete this notification");
        }

        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public void sendSystemNotification(User recipient, String title, String content, String type) {
        Notification notification = Notification.builder()
                .title(title)
                .content(content)
                .type(type != null ? type : "SYSTEM")
                .recipient(recipient)
                .sender(null)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .recipientId(notification.getRecipient() != null ? notification.getRecipient().getId() : null)
                .recipientName(notification.getRecipient() != null ? notification.getRecipient().getFullName() : null)
                .senderId(notification.getSender() != null ? notification.getSender().getId() : null)
                .senderName(notification.getSender() != null ? notification.getSender().getFullName() : "Hệ thống")
                .targetRole(notification.getTargetRole())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

