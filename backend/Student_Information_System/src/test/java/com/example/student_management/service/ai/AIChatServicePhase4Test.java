package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIChatRequest;
import com.example.student_management.dto.ai.AIChatResponse;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.repository.ai.AIConversationRepository;
import com.example.student_management.repository.ai.AIMessageRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.impl.AIChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIChatServicePhase4Test {

    @Mock private UserRepository userRepository;
    @Mock private AIConversationRepository conversationRepository;
    @Mock private AIMessageRepository messageRepository;
    @Mock private AIUsageLogRepository usageLogRepository;
    @Mock private AIChatToolsService toolsService;
    @Mock private AIProviderService providerService;

    @InjectMocks private AIChatServiceImpl aiChatService;

    private User studentUser;
    private AIConversation conversation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);
        Student student = Student.builder().id(10L).studentCode("SV24001").fullName("Nguyễn Văn An")
                .status(AccountStatus.ACTIVE).build();
        studentUser = User.builder().id(1L).username("student_an").role(Role.STUDENT).student(student).build();
        student.setUser(studentUser);
        conversation = AIConversation.builder().id(100L).student(student).title("Hội thoại học tập mới")
                .createdAt(LocalDateTime.now()).messages(new ArrayList<>()).build();
    }

    @Test
    void pureTutorQuestionDoesNotFetchSisData() {
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(studentUser));
        when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(conversation));
        when(usageLogRepository.countByStudentIdAndCreatedAtAfter(eq(10L), any(LocalDateTime.class))).thenReturn(0L);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(messageRepository.save(any(AIMessage.class))).thenAnswer(invocation -> {
            AIMessage m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(201L);
            return m;
        });
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Giải thích khái niệm SQL")
                        .providerUsed("GEMINI")
                        .modelUsed("gemini-2.0-flash")
                        .success(true)
                        .latencyMs(100)
                        .sources(List.of("AI Tutor Knowledge"))
                        .build());

        AIChatResponse response = aiChatService.sendMessage(
                "student_an", 100L, new AIChatRequest("Giải thích khái niệm SQL"));

        assertEquals("Giải thích khái niệm SQL", response.getContent());
        assertTrue(response.getSources().contains("AI Tutor Knowledge"));
        verify(toolsService, never()).getStudentProfile(any());
        verify(toolsService, never()).getPublishedGrades(any());
        verify(toolsService, never()).getAllEnrollments(any());

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerService).generateChatResponse(contextCaptor.capture(), anyList(), anyString(), anyString(), anyList());
        assertTrue(contextCaptor.getValue().contains("AI Tutor"));
        assertTrue(contextCaptor.getValue().contains("KHÔNG dùng dữ liệu điểm/tín chỉ/lịch học"));
    }

    @Test
    void combinedQuestionKeepsSisDataAndTutorInstructions() {
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(studentUser));
        when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(conversation));
        when(usageLogRepository.countByStudentIdAndCreatedAtAfter(eq(10L), any(LocalDateTime.class))).thenReturn(0L);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(messageRepository.save(any(AIMessage.class))).thenAnswer(invocation -> {
            AIMessage m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(301L);
            return m;
        });
        when(toolsService.getPublishedGrades(any())).thenReturn(List.of());
        when(toolsService.computeGpaAndCredits(anyList())).thenReturn(Map.of(
                "accumulatedCredits", 45, "gpa10", "7.50", "gpa4", "3.00",
                "passedCount", 10, "failedCount", 1,
                "passedCourses", List.of(), "failedCourses", List.of()));
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Giải thích GPA thực tế")
                        .providerUsed("GEMINI")
                        .modelUsed("gemini-2.0-flash")
                        .success(true)
                        .latencyMs(100)
                        .sources(List.of("AI Tutor Knowledge", "Published Grades & Academic Records"))
                        .build());

        aiChatService.sendMessage("student_an", 100L, new AIChatRequest("Giải thích điểm GPA của tôi"));

        verify(toolsService).getPublishedGrades(any());
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerService).generateChatResponse(contextCaptor.capture(), anyList(), anyString(), anyString(), anyList());
        assertTrue(contextCaptor.getValue().contains("HƯỚNG DẪN AI TUTOR"));
        assertTrue(contextCaptor.getValue().contains("DỮ LIỆU SIS ĐƯỢC PHÉP SỬ DỤNG"));
        verify(toolsService, never()).getSemesterBreakdown(anyList());
    }
}
