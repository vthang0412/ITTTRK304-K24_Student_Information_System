package com.example.student_management.dto;

import com.example.student_management.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String title;
    private String content;
    private String type;
    private Long recipientId;
    private String recipientName;
    private Long senderId;
    private String senderName;
    private Role targetRole;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

