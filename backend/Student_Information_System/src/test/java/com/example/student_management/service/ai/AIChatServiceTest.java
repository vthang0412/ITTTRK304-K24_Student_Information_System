package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIChatRequest;
import com.example.student_management.dto.ai.AIChatResponse;
import com.example.student_management.dto.ai.AICreateConversationRequest;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.ForbiddenException;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIChatServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AIConversationRepository conversationRepository;

    @Mock
    private AIMessageRepository messageRepository;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @Mock
    private AIContextService contextService;

    @Mock
    private AIProviderService providerService;

    @InjectMocks
    private AIChatServiceImpl aiChatService;

    private User sampleUser;
    private Student sampleStudent;
    private AIConversation sampleConversation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);

        sampleStudent = Student.builder()
                .id(10L)
                .studentCode("SV24001")
                .fullName("Nguyễn Văn An")
                .email("an.nv@sis.edu.vn")
                .department("Công nghệ thông tin")
                .className("24CNTT1")
                .status(AccountStatus.ACTIVE)
                .build();

        sampleUser = User.builder()
                .id(1L)
                .username("student_an")
                .role(Role.STUDENT)
                .student(sampleStudent)
                .build();

        sampleStudent.setUser(sampleUser);

        sampleConversation = AIConversation.builder()
                .id(100L)
                .student(sampleStudent)
                .title("Hội thoại học tập mới")
                .createdAt(LocalDateTime.now())
                .messages(new ArrayList<>())
                .build();
    }

    @Test
    void createConversation_shouldCreateNewConversationForStudent() {
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(sampleUser));
        when(conversationRepository.save(any(AIConversation.class))).thenAnswer(i -> {
            AIConversation c = i.getArgument(0);
            c.setId(101L);
            return c;
        });

        var result = aiChatService.createConversation("student_an", new AICreateConversationRequest("Kế hoạch kỳ 1"));

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("Kế hoạch kỳ 1", result.getTitle());
        assertEquals(10L, result.getStudentId());
    }

    @Test
    void sendMessage_shouldAnswerQuestionUsingRealSisData() {
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(sampleUser));
        when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(sampleConversation));
        when(usageLogRepository.countByStudentIdAndCreatedAtAfter(eq(10L), any(LocalDateTime.class))).thenReturn(5L);

        when(contextService.buildSystemContextPrompt(any(Student.class), any(User.class)))
                .thenReturn("System prompt with real SIS student data");
        when(contextService.getStudentAcademicSummary(any(Student.class), any(User.class)))
                .thenReturn(Map.of("accumulatedCredits", 45, "gpa10", "3.20"));

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());

        when(providerService.generateChatResponse(anyString(), anyList(), eq("Tôi đã tích lũy bao nhiêu tín chỉ?"), anyMap()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Bạn đã tích lũy được 45 tín chỉ.")
                        .providerUsed("SIS-ASSISTANT-ENGINE")
                        .modelUsed("sis-local-v1")
                        .success(true)
                        .latencyMs(120)
                        .sources(List.of("Academic Records"))
                        .build());

        when(messageRepository.save(any(AIMessage.class))).thenAnswer(i -> {
            AIMessage msg = i.getArgument(0);
            msg.setId(201L);
            return msg;
        });

        AIChatRequest request = new AIChatRequest("Tôi đã tích lũy bao nhiêu tín chỉ?");
        AIChatResponse response = aiChatService.sendMessage("student_an", 100L, request);

        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertEquals("Bạn đã tích lũy được 45 tín chỉ.", response.getContent());
        assertTrue(response.getSources().contains("Academic Records"));

        verify(messageRepository, times(2)).save(any(AIMessage.class));
        verify(conversationRepository).save(sampleConversation);
    }

    @Test
    void sendMessage_whenNonStudentRole_shouldThrowForbiddenException() {
        User lecturerUser = User.builder().id(2L).username("gv_nam").role(Role.LECTURER).build();
        when(userRepository.findByUsername("gv_nam")).thenReturn(Optional.of(lecturerUser));

        AIChatRequest request = new AIChatRequest("Tôi có bao nhiêu điểm?");
        assertThrows(ForbiddenException.class, () -> aiChatService.sendMessage("gv_nam", 100L, request));
    }

    @Test
    void getAcademicOverview_shouldReturnStudentSummary() {
        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(sampleUser));
        when(contextService.getStudentAcademicSummary(sampleStudent, sampleUser))
                .thenReturn(Map.of("studentCode", "SV24001", "accumulatedCredits", 45));

        Map<String, Object> overview = aiChatService.getAcademicOverview("student_an");

        assertNotNull(overview);
        assertEquals("SV24001", overview.get("studentCode"));
        assertEquals(45, overview.get("accumulatedCredits"));
    }
}

