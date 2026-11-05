package com.example.student_management.dto.feedback;

import com.example.student_management.entity.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackMessageDto {
    private Long id;
    private Long feedbackId;
    private Long senderId;
    private String senderUsername;
    private String senderFullName;
    private Role senderRole;
    private String message;
    private LocalDateTime createdAt;
}

