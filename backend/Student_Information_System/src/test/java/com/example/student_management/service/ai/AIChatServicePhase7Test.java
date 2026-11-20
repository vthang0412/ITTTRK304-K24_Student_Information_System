package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIChatRequest;
import com.example.student_management.dto.ai.AIChatResponse;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIChatServicePhase7Test {

    @Mock private UserRepository userRepository;
    @Mock private AIConversationRepository conversationRepository;
    @Mock private AIMessageRepository messageRepository;
    @Mock private AIUsageLogRepository usageLogRepository;
    @Mock private AIChatToolsService toolsService;
    @Mock private AIProviderService providerService;
    @Mock private AICourseAdvisorService courseAdvisorService;
    @Mock private AIInsightsService insightsService;

    @InjectMocks private AIChatServiceImpl aiChatService;

    private User studentUser;
    private AIConversation conversation;
    private AtomicLong messageId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);
        Student student = Student.builder().id(10L).studentCode("SV24001").fullName("Nguyễn Văn An")
                .status(AccountStatus.ACTIVE).build();
        studentUser = User.builder().id(1L).username("student_an").role(Role.STUDENT).student(student).build();
        student.setUser(studentUser);
        conversation = AIConversation.builder().id(100L).student(student).title("Hội thoại học tập mới")
                .createdAt(LocalDateTime.now()).messages(new ArrayList<>()).build();
        messageId = new AtomicLong(200L);
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(studentUser));
        when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(conversation));
        when(usageLogRepository.countByStudentIdAndCreatedAtAfter(eq(10L), any(LocalDateTime.class))).thenReturn(0L);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(messageRepository.save(any(AIMessage.class))).thenAnswer(invocation -> {
            AIMessage m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(messageId.incrementAndGet());
            return m;
        });
    }

    @Test
    void courseAdvisorUsesAdvisorServiceAndSkipsChatProvider() {
        when(courseAdvisorService.getCourseRecommendations("student_an")).thenReturn(
                CourseAdvisorResponseDto.builder()
                        .accumulatedCredits(45).activeCredits(12).maxCreditsPerSemester(24)
                        .recommendedCourses(List.of())
                        .notRecommendedCourses(List.of())
                        .disclaimer("Chỉ tham khảo").build());

        AIChatResponse response = aiChatService.sendMessage("student_an", 100L,
                new AIChatRequest("Học kỳ tới nên đăng ký môn nào?"));

        assertEquals("COURSE_ADVISOR", response.getIntent());
        assertTrue(response.getContent().contains("Tư vấn môn học kỳ tới"), response.getContent());
        assertTrue(response.getSources().contains("Course Catalog"));
        verify(providerService, never()).generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList());
        verify(toolsService, never()).getPublishedGrades(any());
    }

    @Test
    void quizGenerationAndAnswerStayWithinConversation() {
        AIChatResponse created = aiChatService.sendMessage("student_an", 100L,
                new AIChatRequest("Tạo 3 câu trắc nghiệm Java"));
        assertEquals("QUIZ_GENERATION", created.getIntent());
        assertTrue(created.getContent().contains("Chưa hiển thị đáp án đúng"), created.getContent());

        AIChatResponse graded = aiChatService.sendMessage("student_an", 100L,
                new AIChatRequest("1. A, 2. A, 3. A"));
        assertEquals("QUIZ_ANSWER_SUBMISSION", graded.getIntent());
        assertTrue(graded.getContent().contains("3/3"), graded.getContent());
        assertTrue(graded.getSources().contains("AI Quiz Bank (Backend)"));
        verify(providerService, never()).generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList());
    }

    @Test
    void unknownQuestionGetsStructuredClarificationWithoutProvider() {
        AIChatResponse response = aiChatService.sendMessage("student_an", 100L,
                new AIChatRequest("xyz abc"));

        assertEquals("UNKNOWN", response.getIntent());
        assertTrue(response.getNeedsClarification());
        assertTrue(response.getContent().contains("chưa xác định được"), response.getContent());
        verify(providerService, never()).generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList());
    }

    @Test
    void responseIncludesConfidenceAndContextMetadata() {
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Giải thích khái niệm SQL")
                        .providerUsed("GEMINI")
                        .modelUsed("gemini-2.0-flash")
                        .success(true)
                        .latencyMs(100)
                        .sources(List.of("AI Tutor Knowledge"))
                        .build());

        AIChatResponse response = aiChatService.sendMessage("student_an", 100L,
                new AIChatRequest("Giải thích khái niệm SQL"));

        assertNotNull(response.getConfidence());
        assertTrue(response.getConfidence() >= 0.0 && response.getConfidence() <= 1.0);
        assertFalse(response.getNeedsClarification());
        assertNotNull(response.getContextUsed());
    }
}
