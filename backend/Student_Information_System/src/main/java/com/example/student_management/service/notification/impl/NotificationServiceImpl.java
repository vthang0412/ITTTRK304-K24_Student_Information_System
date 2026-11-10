package com.example.student_management.service.notification.impl;

import com.example.student_management.dto.notification.NotificationDto;
import com.example.student_management.dto.notification.NotificationRequest;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.User;
import com.example.student_management.entity.notification.Notification;
import com.example.student_management.entity.notification.NotificationRead;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.notification.NotificationReadRepository;
import com.example.student_management.repository.notification.NotificationRepository;
import com.example.student_management.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(String username) {
        User user = currentUser(username);
        Set<Long> readIds = notificationReadRepository.findReadNotificationIdsByUserId(user.getId());
        return notificationRepository.findNotificationsForUser(user.getId(), user.getRole())
                .stream()
                .map(n -> {
                    NotificationDto dto = toDto(n);
                    if (n.getRecipient() == null) {
                        dto.setIsRead(readIds.contains(n.getId()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = currentUser(username);
        Set<Long> readIds = notificationReadRepository.findReadNotificationIdsByUserId(user.getId());
        List<Notification> all = notificationRepository.findNotificationsForUser(user.getId(), user.getRole());
        return all.stream().filter(n -> {
            if (n.getRecipient() != null) {
                return !Boolean.TRUE.equals(n.getIsRead());
            } else {
                return !readIds.contains(n.getId());
            }
        }).count();
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

        if (notification.getRecipient() != null) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        } else {
            // Thông báo chung: lưu trạng thái đã đọc riêng cho user này vào bảng notification_reads
            if (!notificationReadRepository.existsByUserIdAndNotificationId(user.getId(), notification.getId())) {
                notificationReadRepository.save(NotificationRead.builder()
                        .userId(user.getId())
                        .notificationId(notification.getId())
                        .build());
            }
        }

        NotificationDto dto = toDto(notification);
        dto.setIsRead(true);
        return dto;
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        User user = currentUser(username);
        notificationRepository.markAllAsReadForUser(user.getId());

        List<Notification> globals = notificationRepository.findNotificationsForUser(user.getId(), user.getRole())
                .stream().filter(n -> n.getRecipient() == null).collect(Collectors.toList());
        Set<Long> alreadyRead = notificationReadRepository.findReadNotificationIdsByUserId(user.getId());
        List<NotificationRead> toSave = globals.stream()
                .filter(n -> !alreadyRead.contains(n.getId()))
                .map(n -> NotificationRead.builder().userId(user.getId()).notificationId(n.getId()).build())
                .collect(Collectors.toList());
        if (!toSave.isEmpty()) {
            notificationReadRepository.saveAll(toSave);
        }
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

