package com.example.student_management.service.ai.impl;

import com.example.student_management.dto.ai.*;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.ai.AIUsageLog;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.ai.AIConversationRepository;
import com.example.student_management.repository.ai.AIMessageRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.AIChatService;
import com.example.student_management.service.ai.AIContextService;
import com.example.student_management.service.ai.AIProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIChatServiceImpl implements AIChatService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AIConversationRepository conversationRepository;

    @Autowired
    private AIMessageRepository messageRepository;

    @Autowired
    private AIUsageLogRepository usageLogRepository;

    @Autowired
    private AIContextService contextService;

    @Autowired
    private AIProviderService providerService;

    @Value("${app.ai.rate-limit-per-hour:60}")
    private int rateLimitPerHour;

    @Override
    @Transactional(readOnly = true)
    public List<AIConversationDto> getConversations(String username) {
        Student student = getStudent(username);
        return conversationRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .map(this::toConversationDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AIConversationDto createConversation(String username, AICreateConversationRequest request) {
        Student student = getStudent(username);
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Hội thoại học tập mới";

        AIConversation conversation = AIConversation.builder()
                .student(student)
                .title(title)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AIConversation saved = conversationRepository.save(conversation);
        return toConversationDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AIMessageDto> getMessages(String username, Long conversationId) {
        Student student = getStudent(username);
        AIConversation conversation = getOwnedConversation(conversationId, student.getId());

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AIChatResponse sendMessage(String username, Long conversationId, AIChatRequest request) {
        User user = getUser(username);
        Student student = user.getStudent();
        if (student == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới được sử dụng Trợ lý học tập AI");
        }

        AIConversation conversation = getOwnedConversation(conversationId, student.getId());

        // Kiểm tra giới hạn tần suất gọi (Rate limiting: AI-BR10)
        long recentCalls = usageLogRepository.countByStudentIdAndCreatedAtAfter(student.getId(), LocalDateTime.now().minusHours(1));
        if (recentCalls >= rateLimitPerHour) {
            throw new BadRequestException("Bạn đã dùng hết hạn mức " + rateLimitPerHour + " lượt hỏi trong 1 giờ. Vui lòng chờ ít phút rồi thử lại.");
        }

        String question = request.getMessage().trim();

        // 1. Lưu tin nhắn của USER
        AIMessage userMessage = AIMessage.builder()
                .conversation(conversation)
                .role("USER")
                .content(question)
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(userMessage);

        // 2. Thu thập dữ liệu thực tế của sinh viên (SIS context)
        String systemContext = contextService.buildSystemContextPrompt(student, user);
        Map<String, Object> academicSummary = contextService.getStudentAcademicSummary(student, user);

        // 3. Lấy lịch sử hội thoại gần đây
        List<AIMessage> previousMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<Map<String, String>> historyList = new ArrayList<>();
        for (AIMessage m : previousMessages) {
            historyList.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        // 4. Gọi AI Provider hoặc Smart SIS Fallback Engine
        AIProviderService.AIProviderResult result = providerService.generateChatResponse(
                systemContext,
                historyList,
                question,
                academicSummary
        );

        // 5. Lưu tin nhắn trả lời của ASSISTANT
        String sourcesStr = (result.getSources() != null && !result.getSources().isEmpty())
                ? String.join(", ", result.getSources())
                : "Academic Records";

        AIMessage assistantMessage = AIMessage.builder()
                .conversation(conversation)
                .role("ASSISTANT")
                .content(result.getContent())
                .sources(sourcesStr)
                .createdAt(LocalDateTime.now())
                .build();
        AIMessage savedAssistant = messageRepository.save(assistantMessage);

        // Cập nhật tiêu đề hội thoại nếu là câu hỏi đầu tiên
        if ("Hội thoại học tập mới".equals(conversation.getTitle())) {
            String autoTitle = question.length() > 40 ? question.substring(0, 37) + "..." : question;
            conversation.setTitle(autoTitle);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // 6. Ghi log sử dụng AI (AI-BR10)
        try {
            AIUsageLog log = AIUsageLog.builder()
                    .student(student)
                    .featureType("CHAT")
                    .provider(result.getProviderUsed())
                    .model(result.getModelUsed())
                    .status(result.isSuccess() ? "SUCCESS" : "FAILED")
                    .latencyMs(result.getLatencyMs())
                    .createdAt(LocalDateTime.now())
                    .build();
            usageLogRepository.save(log);
        } catch (Exception ignored) {}

        return AIChatResponse.builder()
                .messageId(savedAssistant.getId())
                .conversationId(conversation.getId())
                .role("ASSISTANT")
                .content(savedAssistant.getContent())
                .sources(result.getSources())
                .createdAt(savedAssistant.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteConversation(String username, Long conversationId) {
        Student student = getStudent(username);
        AIConversation conversation = getOwnedConversation(conversationId, student.getId());
        conversationRepository.delete(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAcademicOverview(String username) {
        User user = getUser(username);
        Student student = user.getStudent();
        if (student == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới được truy cập dữ liệu học tập này");
        }
        return contextService.getStudentAcademicSummary(student, user);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    private Student getStudent(String username) {
        User user = getUser(username);
        if (user.getRole() != Role.STUDENT || user.getStudent() == null) {
            throw new ForbiddenException("Chỉ tài khoản sinh viên mới có quyền sử dụng chức năng này");
        }
        return user.getStudent();
    }

    private AIConversation getOwnedConversation(Long conversationId, Long studentId) {
        return conversationRepository.findByIdAndStudentId(conversationId, studentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hội thoại #" + conversationId + " hoặc bạn không có quyền truy cập"));
    }

    private AIConversationDto toConversationDto(AIConversation c) {
        List<AIMessage> msgs = c.getMessages();
        int count = msgs != null ? msgs.size() : 0;
        String lastMsg = (msgs != null && !msgs.isEmpty()) ? msgs.get(msgs.size() - 1).getContent() : null;
        if (lastMsg != null && lastMsg.length() > 60) {
            lastMsg = lastMsg.substring(0, 57) + "...";
        }

        return AIConversationDto.builder()
                .id(c.getId())
                .studentId(c.getStudent().getId())
                .title(c.getTitle())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt())
                .messageCount(count)
                .lastMessage(lastMsg)
                .build();
    }

    private AIMessageDto toMessageDto(AIMessage m) {
        List<String> sourcesList = (m.getSources() != null && !m.getSources().isBlank())
                ? Arrays.stream(m.getSources().split(",")).map(String::trim).collect(Collectors.toList())
                : List.of("Academic Records");

        return AIMessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .role(m.getRole())
                .content(m.getContent())
                .sources(sourcesList)
                .createdAt(m.getCreatedAt())
                .build();
    }
}

