package com.example.student_management.controller.ai;

import com.example.student_management.dto.ai.*;
import com.example.student_management.service.ai.AIChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/ai")
@PreAuthorize("hasRole('STUDENT')")
public class AIChatController {

    @Autowired
    private AIChatService aiChatService;

    /**
     * 1. Lấy tổng quan thông tin học tập thực tế (GPA, tín chỉ, môn học)
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", aiChatService.getAcademicOverview(authentication.getName()));
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Lấy danh sách các cuộc hội thoại AI của sinh viên
     */
    @GetMapping("/chat/conversations")
    public ResponseEntity<Map<String, Object>> getConversations(Authentication authentication) {
        List<AIConversationDto> conversations = aiChatService.getConversations(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", conversations);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Khởi tạo một cuộc hội thoại mới
     */
    @PostMapping("/chat/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestBody(required = false) AICreateConversationRequest request,
            Authentication authentication
    ) {
        AIConversationDto created = aiChatService.createConversation(authentication.getName(), request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", created);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Xem lịch sử tin nhắn trong một cuộc hội thoại
     */
    @GetMapping("/chat/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<AIMessageDto> messages = aiChatService.getMessages(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", messages);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Gửi câu hỏi cho AI Study Assistant
     */
    @PostMapping("/chat/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody AIChatRequest request,
            Authentication authentication
    ) {
        AIChatResponse result = aiChatService.sendMessage(authentication.getName(), id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Xóa cuộc hội thoại
     */
    @DeleteMapping("/chat/conversations/{id}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        aiChatService.deleteConversation(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã xóa cuộc hội thoại thành công");
        return ResponseEntity.ok(response);
    }
}

