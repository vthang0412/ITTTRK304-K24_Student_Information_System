package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIChatRequest;
import com.example.student_management.dto.ai.AIChatResponse;
import com.example.student_management.entity.ai.AIConversation;
import com.example.student_management.entity.ai.AIMessage;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.TooManyRequestsException;
import com.example.student_management.repository.ai.AIConversationRepository;
import com.example.student_management.repository.ai.AIMessageRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.impl.AIChatServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AIPhase8LongResponseTest {

    private static final String QUESTION = "Giải thích khái niệm SQL";

    @Mock private UserRepository userRepository;
    @Mock private AIConversationRepository conversationRepository;
    @Mock private AIMessageRepository messageRepository;
    @Mock private AIUsageLogRepository usageLogRepository;
    @Mock private AIChatToolsService toolsService;
    @Mock private AIProviderService providerService;
    @Mock private AICourseAdvisorService courseAdvisorService;
    @Mock private AIInsightsService insightsService;

    @InjectMocks private AIChatServiceImpl aiChatService;

    private final AIResponseContinuationService continuationService = new AIResponseContinuationService();
    private AtomicLong messageId;
    private AtomicReference<AIMessage> savedAssistant;

    @BeforeEach
    void setUp() {
        AIRequestQuota.resetForTests();
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);
        ReflectionTestUtils.setField(aiChatService, "maxContinuations", 3);
        ReflectionTestUtils.setField(aiChatService, "continuationService", continuationService);

        Student student = Student.builder().id(10L).studentCode("SV24001").fullName("Nguyễn Văn An")
                .status(AccountStatus.ACTIVE).build();
        User studentUser = User.builder().id(1L).username("student_an").role(Role.STUDENT).student(student).build();
        student.setUser(studentUser);
        AIConversation conversation = AIConversation.builder().id(100L).student(student)
                .title("Hội thoại học tập mới").createdAt(LocalDateTime.now()).messages(new ArrayList<>()).build();
        messageId = new AtomicLong(200L);
        savedAssistant = new AtomicReference<>();

        when(userRepository.findByUsername("student_an")).thenReturn(Optional.of(studentUser));
        when(conversationRepository.findByIdAndStudentId(100L, 10L)).thenReturn(Optional.of(conversation));
        when(usageLogRepository.countByStudentIdAndCreatedAtAfter(eq(10L), any(LocalDateTime.class))).thenReturn(0L);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L)).thenReturn(List.of());
        when(messageRepository.save(any(AIMessage.class))).thenAnswer(invocation -> {
            AIMessage m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(messageId.incrementAndGet());
            if ("ASSISTANT".equalsIgnoreCase(m.getRole())) savedAssistant.set(m);
            return m;
        });
    }

    private void stubProvider(String content, String finishReason, boolean truncated, boolean canContinue) {
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content(content)
                        .providerUsed("OPENROUTER")
                        .modelUsed("google/gemini-2.5-flash")
                        .success(true)
                        .latencyMs(120)
                        .sources(List.of("AI Tutor Knowledge"))
                        .finishReason(finishReason)
                        .truncated(truncated)
                        .canContinue(canContinue)
                        .build());
    }

    @Test
    void finishReasonIsParsedFromEachProvider() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("length", AIProviderService.extractFinishReason(
                mapper.readTree("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"x\"}}]}"),
                "OPENROUTER"));
        assertEquals("MAX_TOKENS", AIProviderService.extractFinishReason(
                mapper.readTree("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"), "GEMINI"));
        assertEquals("LENGTH", AIProviderService.normalizeFinishReason("max_tokens"));
        assertEquals("STOP", AIProviderService.normalizeFinishReason("stop"));
        assertEquals("SAFETY", AIProviderService.normalizeFinishReason("SAFETY"));
        assertEquals("UNKNOWN", AIProviderService.normalizeFinishReason(null));
    }

    @Test
    void geminiJoinsTextPartsSkipsEmptyPartsAndKeepsMaxTokensFinishReason() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var parts = mapper.readTree("""
                [
                  {"text":"Phần đầu "},
                  {"inlineData":{"mimeType":"image/png","data":"ignored"}},
                  {"text":""},
                  {"text":"phần sau"}
                ]
                """);
        assertEquals("Phần đầu phần sau", AIProviderService.extractGeminiTextParts(parts));

        var response = mapper.readTree("""
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"x"}]}}]}
                """);
        assertEquals("MAX_TOKENS", AIProviderService.extractFinishReason(response, "GEMINI"));
        assertEquals("LENGTH", AIProviderService.normalizeFinishReason(
                AIProviderService.extractFinishReason(response, "GEMINI")));
    }

    @Test
    void boundedIntFallsBackOutsideSafeRange() {
        assertEquals(2048, AIProviderService.boundedInt(99999, 256, 4096, 2048));
        assertEquals(512, AIProviderService.boundedInt(512, 256, 4096, 2048));
        assertEquals(12000, AIProviderService.boundedInt(1, 1000, 16000, 12000));
    }

    @Test
    void longProviderAnswerWithLengthIsMarkedTruncatedAndContinuable() {
        stubProvider("Phần đầu của tài liệu kỹ thuật dài", "LENGTH", true, true);

        AIChatResponse response = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));

        assertEquals("LENGTH", response.getFinishReason());
        assertTrue(response.getTruncated());
        assertTrue(response.getCanContinue());
        assertEquals("OPENROUTER", response.getProviderUsed());
        assertEquals("google/gemini-2.5-flash", response.getModelUsed());
        assertEquals(0, response.getContinuationCount());
    }

    @Test
    void shortAnswerHasContinuationDisabled() {
        stubProvider("SQL là ngôn ngữ truy vấn.", "STOP", false, false);

        AIChatResponse response = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));

        assertFalse(response.getTruncated());
        assertFalse(response.getCanContinue());
        assertEquals("STOP", response.getFinishReason());
    }

    @Test
    void providerFailureUsesFallbackWithoutContinuation() {
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Hiện tại trợ lý AI chưa thể phản hồi. Vui lòng thử lại sau.")
                        .providerUsed("SIS-ASSISTANT-ENGINE")
                        .modelUsed("sis-local-v1")
                        .success(false)
                        .latencyMs(5)
                        .finishReason("SIS_FALLBACK")
                        .truncated(false)
                        .canContinue(false)
                        .build());

        AIChatResponse response = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));

        assertNotNull(response.getContent());
        assertFalse(response.getCanContinue());
        assertEquals("SIS-ASSISTANT-ENGINE", response.getProviderUsed());
    }

    @Test
    void continuationAppendsWithoutRepeatingPreviousContent() {
        stubProvider("Phần đầu kết thúc ở đây", "LENGTH", true, true);
        AIChatResponse first = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));
        AIMessage partial = savedAssistant.get();
        assertNotNull(partial);

        when(messageRepository.findByIdAndConversationId(first.getMessageId(), 100L))
                .thenReturn(Optional.of(partial));
        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content(" và phần còn lại của câu trả lời.")
                        .providerUsed("OPENROUTER")
                        .modelUsed("google/gemini-2.5-flash")
                        .success(true)
                        .latencyMs(90)
                        .finishReason("STOP")
                        .truncated(false)
                        .canContinue(false)
                        .build());

        AIChatRequest continueRequest = AIChatRequest.builder()
                .message("Tiếp tục câu trả lời")
                .continueResponse(true)
                .continueFromMessageId(first.getMessageId())
                .continuationCount(1)
                .build();
        AIChatResponse second = aiChatService.sendMessage("student_an", 100L, continueRequest);

        assertEquals(first.getMessageId(), second.getMessageId());
        assertTrue(second.getContent().startsWith("Phần đầu kết thúc ở đây"), second.getContent());
        assertTrue(second.getContent().contains("phần còn lại"), second.getContent());
        assertEquals(1, second.getContent().split("Phần đầu kết thúc ở đây", -1).length - 1);
        assertEquals(1, second.getContinuationCount());
        assertFalse(second.getCanContinue());
    }

    @Test
    void providerFailureDoesNotAppendFallbackOrConsumeCountAndCanBeRetried() {
        stubProvider("Phần trả lời dở dang", "LENGTH", true, true);
        AIChatResponse first = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));
        AIMessage partial = savedAssistant.get();
        String originalContent = partial.getContent();
        when(messageRepository.findByIdAndConversationId(first.getMessageId(), 100L))
                .thenReturn(Optional.of(partial));

        when(providerService.generateChatResponse(anyString(), anyList(), anyString(), anyString(), anyList()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Hiện tại trợ lý AI chưa thể phản hồi. Vui lòng thử lại sau.")
                        .providerUsed("SIS-ASSISTANT-ENGINE")
                        .modelUsed("sis-local-v1")
                        .success(false)
                        .latencyMs(5)
                        .finishReason("SIS_FALLBACK")
                        .truncated(false)
                        .canContinue(false)
                        .build())
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .content("Phần tiếp nối.")
                        .providerUsed("OPENROUTER")
                        .modelUsed("google/gemini-2.5-flash")
                        .success(true)
                        .latencyMs(90)
                        .finishReason("STOP")
                        .truncated(false)
                        .canContinue(false)
                        .build());

        AIChatRequest request = continuationRequest(first.getMessageId());
        BadRequestException failure = assertThrows(
                BadRequestException.class,
                () -> aiChatService.sendMessage("student_an", 100L, request));
        assertTrue(failure.getMessage().contains("vui lòng thử lại"));
        assertEquals(originalContent, partial.getContent());
        verify(messageRepository, never()).saveAndFlush(any(AIMessage.class));

        AIResponseContinuationService.ContinuationState available = continuationService.begin(
                first.getMessageId(), "SV24001", 100L, 3);
        assertEquals(0, available.getCount());
        assertEquals(originalContent, available.getPartialContent());
        continuationService.cancel(first.getMessageId());

        AIChatResponse retried = aiChatService.sendMessage("student_an", 100L, request);
        assertEquals(1, retried.getContinuationCount());
        assertTrue(retried.getContent().contains("Phần tiếp nối."));
        assertFalse(retried.getContent().contains("Hiện tại trợ lý AI chưa thể phản hồi."));
    }

    @Test
    void quotaFailureKeepsPartialAndDoesNotConsumeContinuationCount() {
        stubProvider("Phần trả lời dở dang", "LENGTH", true, true);
        AIChatResponse first = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));
        AIMessage partial = savedAssistant.get();
        String originalContent = partial.getContent();
        when(messageRepository.findByIdAndConversationId(first.getMessageId(), 100L))
                .thenReturn(Optional.of(partial));
        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 0);

        assertThrows(TooManyRequestsException.class,
                () -> aiChatService.sendMessage("student_an", 100L, continuationRequest(first.getMessageId())));
        assertEquals(originalContent, partial.getContent());

        AIResponseContinuationService.ContinuationState available = continuationService.begin(
                first.getMessageId(), "SV24001", 100L, 3);
        assertEquals(0, available.getCount());
        continuationService.cancel(first.getMessageId());

        ReflectionTestUtils.setField(aiChatService, "rateLimitPerHour", 60);
        stubProvider("Phần tiếp nối.", "STOP", false, false);
        AIChatResponse retried = aiChatService.sendMessage(
                "student_an", 100L, continuationRequest(first.getMessageId()));
        assertEquals(1, retried.getContinuationCount());
    }

    @Test
    void concurrentContinueRequestsCannotWriteTheSameMessageAtOnce() throws Exception {
        stubProvider("Phần trả lời dở dang", "LENGTH", true, true);
        AIChatResponse first = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));
        AIMessage partial = savedAssistant.get();
        when(messageRepository.findByIdAndConversationId(first.getMessageId(), 100L))
                .thenReturn(Optional.of(partial));

        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        doAnswer(invocation -> {
            providerStarted.countDown();
            if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release mocked provider");
            }
            return AIProviderService.AIProviderResult.builder()
                    .content("Phần tiếp nối.")
                    .providerUsed("OPENROUTER")
                    .modelUsed("google/gemini-2.5-flash")
                    .success(true)
                    .latencyMs(90)
                    .finishReason("STOP")
                    .truncated(false)
                    .canContinue(false)
                    .build();
        }).when(providerService).generateChatResponse(
                anyString(), anyList(), anyString(), anyString(), anyList());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AIChatResponse> firstRequest = executor.submit(
                    () -> aiChatService.sendMessage(
                            "student_an", 100L, continuationRequest(first.getMessageId())));
            assertTrue(providerStarted.await(5, TimeUnit.SECONDS));

            assertThrows(ConflictException.class,
                    () -> aiChatService.sendMessage(
                            "student_an", 100L, continuationRequest(first.getMessageId())));
            releaseProvider.countDown();

            AIChatResponse response = firstRequest.get(5, TimeUnit.SECONDS);
            assertEquals(1, response.getContinuationCount());
            assertTrue(response.getContent().contains("Phần tiếp nối."));
            verify(providerService, times(2)).generateChatResponse(
                    anyString(), anyList(), anyString(), anyString(), anyList());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mergeContinuationRemovesOnlyClearBoundaryOverlap() {
        String contextTail = ("Đoạn cuối này được lặp lại nguyên văn tại ranh giới giữa hai phần. ")
                .repeat(3).stripTrailing();
        String partial = contextTail;
        String continuation = contextTail + "Phần tiếp theo.";

        String merged = ReflectionTestUtils.invokeMethod(
                aiChatService, "mergeContinuation", partial, continuation, contextTail);
        assertEquals(partial + "\nPhần tiếp theo.", merged);
    }

    @Test
    void mergeContinuationPreservesNonOverlappingAndExplicitlyQuotedText() {
        String partial = "Đây là nội dung trước.";
        String continuation = "Đây là phần mới.";
        String merged = ReflectionTestUtils.invokeMethod(
                aiChatService, "mergeContinuation", partial, continuation, partial);
        assertEquals(partial + "\n" + continuation, merged);

        String repeatedParagraph = ("Đoạn được trích dẫn nguyên văn và phải được bảo toàn. ").repeat(3).stripTrailing();
        String quoted = "> " + repeatedParagraph + "\n";
        String quotedMerge = ReflectionTestUtils.invokeMethod(
                aiChatService, "mergeContinuation", repeatedParagraph, quoted, repeatedParagraph);
        assertTrue(quotedMerge.endsWith("\n" + quoted));
    }

    @Test
    void continuationHonoursMaxCountAndOwnership() {
        stubProvider("Phần đầu", "LENGTH", true, true);
        AIChatResponse first = aiChatService.sendMessage("student_an", 100L, new AIChatRequest(QUESTION));
        AIMessage partial = savedAssistant.get();
        when(messageRepository.findByIdAndConversationId(first.getMessageId(), 100L))
                .thenReturn(Optional.of(partial));
        assertThrows(BadRequestException.class,
                () -> continuationService.begin(first.getMessageId(), "another-student", 100L, 3));

        ReflectionTestUtils.setField(aiChatService, "maxContinuations", 1);
        AIChatRequest request = continuationRequest(first.getMessageId());
        AIChatResponse continued = aiChatService.sendMessage("student_an", 100L, request);
        assertEquals(1, continued.getContinuationCount());
        assertFalse(continued.getCanContinue());
        BadRequestException maxCountError = assertThrows(
                BadRequestException.class, () -> aiChatService.sendMessage("student_an", 100L, request));
        assertTrue(maxCountError.getMessage().contains("tối đa"));

        AIChatRequest unknown = AIChatRequest.builder()
                .message("Tiếp tục câu trả lời")
                .continueResponse(true)
                .continueFromMessageId(999L)
                .build();
        assertThrows(BadRequestException.class, () -> aiChatService.sendMessage("student_an", 100L, unknown));
    }

    private AIChatRequest continuationRequest(Long messageId) {
        return AIChatRequest.builder()
                .message("Tiếp tục câu trả lời")
                .continueResponse(true)
                .continueFromMessageId(messageId)
                .build();
    }
}
