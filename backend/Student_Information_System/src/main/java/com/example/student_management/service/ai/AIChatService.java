package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.*;

import java.util.List;
import java.util.Map;

public interface AIChatService {
    List<AIConversationDto> getConversations(String username);

    AIConversationDto createConversation(String username, AICreateConversationRequest request);

    List<AIMessageDto> getMessages(String username, Long conversationId);

    AIChatResponse sendMessage(String username, Long conversationId, AIChatRequest request);

    void deleteConversation(String username, Long conversationId);

    Map<String, Object> getAcademicOverview(String username);
}

