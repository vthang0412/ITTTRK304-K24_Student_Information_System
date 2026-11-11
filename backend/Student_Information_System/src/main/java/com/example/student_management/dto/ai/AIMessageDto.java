package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIMessageDto {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private List<String> sources;
    private LocalDateTime createdAt;
}

