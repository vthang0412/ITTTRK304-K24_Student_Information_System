package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIMessageDto;
import com.example.student_management.dto.ai.AIRenameConversationRequest;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIChatServicePhase5Test {

    @Mock private UserRepository userRepository;
    @Mock private AIConversationRepository conversationRepository;
    @Mock private AIMessageRepository messageRepository;
    @Mock private AIUsageLogRepository usageLogRepository;
    @Mock private AIChatToolsService toolsService;
    @Mock private AIProviderService providerService;

    @InjectMocks private AIChatServiceImpl aiChatService;

    private Student student;
    private User studentUser;
    private AIConversation conversation;
    private AIMessage assistantMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);
        student = Student.builder().id(10L).studentCode("SV24001").fullName("Nguyễn Văn An")
                .email("an@sis.edu.vn").status(AccountStatus.ACTIVE).build();
        studentUser = User.builder().id(1L).username("student_an").role(Role.STUDENT).student(student).build();
        student.setUser(studentUser);
        conversation = AIConversation.builder().id(100L).student(student).title("Hội thoại học tập mới")
                .createdAt(LocalDateTime.now()).messages(new ArrayList<>()).build();
        assistantMessage = AIMessage.builder().id(500L).conversation(conversation).role("ASSISTANT")
                .content("Gợi ý ôn tập").createdAt(LocalDateTime.now()).build();

        lenient().when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(studentUser));
        lenient().when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(conversation));
        lenient().when(conversationRepository.save(any(AIConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messageRepository.save(any(AIMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void renameConversationKeepsOwnershipAndUpdatesTitle() {
        AIRenameConversationRequest request = new AIRenameConversationRequest("Ôn thi Java");

        assertEquals("Ôn thi Java", aiChatService.renameConversation("student_an", 100L, request).getTitle());
        assertEquals("Ôn thi Java", conversation.getTitle());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void feedbackOnlyAcceptsAssistantMessagesAndKnownValues() {
        when(messageRepository.findById(500L)).thenReturn(Optional.of(assistantMessage));

        AIMessageDto result = aiChatService.setMessageFeedback("student_an", 500L, "helpful");

        assertEquals("HELPFUL", result.getFeedback());
        assertEquals("HELPFUL", assistantMessage.getFeedback());
        assertThrows(BadRequestException.class,
                () -> aiChatService.setMessageFeedback("student_an", 500L, "SPAM"));
        verify(messageRepository).save(assistantMessage);
    }

    @Test
    void feedbackRejectsOtherStudentsMessages() {
        Student otherStudent = Student.builder().id(11L).studentCode("SV24002").build();
        AIConversation otherConversation = AIConversation.builder().id(101L).student(otherStudent).build();
        AIMessage otherMessage = AIMessage.builder().id(501L).conversation(otherConversation)
                .role("ASSISTANT").content("Câu trả lời").build();
        when(messageRepository.findById(501L)).thenReturn(Optional.of(otherMessage));

        assertThrows(ForbiddenException.class,
                () -> aiChatService.setMessageFeedback("student_an", 501L, "HELPFUL"));
        verify(messageRepository, never()).save(any(AIMessage.class));
    }

    @Test
    void academicReportUsesRealSisDataWithoutProvider() {
        Map<String, Object> gpa = Map.of(
                "accumulatedCredits", 45,
                "gpa10", new BigDecimal("7.50"),
                "gpa4", new BigDecimal("3.00"),
                "passedCount", 10,
                "failedCount", 1,
                "passedCourses", List.of(),
                "failedCourses", List.of(Map.of("courseCode", "JAVA01", "courseName", "Java cơ bản")));
        lenient().when(toolsService.getStudentProfile(student)).thenReturn(Map.of(
                "studentCode", "SV24001", "fullName", "Nguyễn Văn An"));
        lenient().when(toolsService.getPublishedGrades(studentUser)).thenReturn(List.<StudentGradeDto>of());
        lenient().when(toolsService.computeGpaAndCredits(anyList())).thenReturn(gpa);
        lenient().when(toolsService.getAllEnrollments(student)).thenReturn(List.of());
        lenient().when(toolsService.summarizeEnrollments(anyList())).thenReturn(Map.<String, Object>of(
                "items", List.of(), "activeCredits", 6));
        when(toolsService.getSemesterBreakdown(anyList())).thenReturn(List.of(Map.of("semester", "HK1 2026")));

        Map<String, Object> report = aiChatService.getAcademicReport("student_an");

        assertEquals("SV24001", report.get("studentCode"));
        assertEquals(new BigDecimal("7.50"), report.get("gpa10"));
        assertEquals(45, report.get("accumulatedCredits"));
        assertTrue(report.containsKey("generatedAt"));
        assertTrue(((List<?>) report.get("sources")).contains("Academic Records"));
        verifyNoInteractions(providerService);
    }
}
