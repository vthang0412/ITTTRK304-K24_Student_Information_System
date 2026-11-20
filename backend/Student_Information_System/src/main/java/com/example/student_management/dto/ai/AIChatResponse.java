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
public class AIChatResponse {
    private Long messageId;
    private Long conversationId;
    private String role;
    private String content;
    private List<String> sources;
    private String intent;
    private Double confidence;
    private Boolean needsClarification;
    private Boolean contextUsed;
    private LocalDateTime createdAt;
    private String finishReason;
    private Boolean truncated;
    private Boolean canContinue;
    private String providerUsed;
    private String modelUsed;
    private Integer continuationCount;
}

