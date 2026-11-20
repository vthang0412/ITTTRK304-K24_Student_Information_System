package com.example.student_management.service.ai;

import com.example.student_management.entity.ai.AIMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AIPhase7IntentContextTest {

    private final AIIntentService intentService = new AIIntentService();
    private final AIConversationContextService contextService = new AIConversationContextService();

    @Test
    void detectsAllPhase7BusinessIntents() {
        assertEquals(AIIntentService.Intent.COURSE_ADVISOR,
                intentService.detectIntent("Học kỳ tới nên đăng ký môn nào?", false));
        assertEquals(AIIntentService.Intent.GPA_WHAT_IF,
                intentService.detectIntent("Nếu GPA kỳ tới là 3.5 thì GPA tích lũy thế nào?", false));
        assertEquals(AIIntentService.Intent.STUDY_PLANNER,
                intentService.detectIntent("Tạo kế hoạch học tập 14 ngày", false));
        assertEquals(AIIntentService.Intent.QUIZ_GENERATION,
                intentService.detectIntent("Tạo 10 câu hỏi Java Core", false));
        assertEquals(AIIntentService.Intent.TIMETABLE_ASSISTANT,
                intentService.detectIntent("Phân tích lịch học và lịch thi", false));
        assertEquals(AIIntentService.Intent.LEARNING_INSIGHTS,
                intentService.detectIntent("Phân tích kết quả học tập", false));
    }

    @Test
    void generalKnowledgeStaysAsTutorIntent() {
        AIIntentService.Intent intent = intentService.detectIntent("Giải thích Spring Boot Security", false);
        assertTrue(intentService.isTutorIntent(intent), "Actual: " + intent);
    }

    @Test
    void tutorFollowUpKeepsPreviousTopic() {
        AIConversationContextService.Context ctx = contextService.resolve(
                List.of(message("USER", "Giải thích JWT")), "Cho tôi một ví dụ cụ thể");
        assertTrue(ctx.isFollowUp());
        assertTrue(ctx.getTopic().contains("JWT"), ctx.getTopic());

        AIIntentService.IntentAnalysis analysis =
                intentService.analyze("Cho tôi một ví dụ cụ thể", true, ctx.getPrior());
        assertEquals(AIIntentService.Intent.TUTOR_FOLLOW_UP, analysis.getIntent());
        assertTrue(analysis.getTutorPlan().getTopic().contains("JWT"), analysis.getTutorPlan().getTopic());
    }

    @Test
    void codeFollowUpKeepsJwtTopic() {
        AIConversationContextService.Context ctx = contextService.resolve(
                List.of(message("USER", "Giải thích JWT")), "Có code Java không?");
        AIIntentService.IntentAnalysis analysis =
                intentService.analyze("Có code Java không?", true, ctx.getPrior());
        assertEquals(AIIntentService.Intent.TUTOR_FOLLOW_UP, analysis.getIntent());
        assertTrue(analysis.getTutorPlan().getTopic().contains("JWT"));
    }

    @Test
    void quizAnswerFollowUpTargetsCurrentQuiz() {
        AIConversationContextService.Context ctx = contextService.resolve(
                List.of(message("USER", "Tạo 3 câu trắc nghiệm Java")), "Đây là đáp án của tôi");
        AIIntentService.IntentAnalysis analysis =
                intentService.analyze("Đây là đáp án của tôi", true, ctx.getPrior());
        assertEquals(AIIntentService.Intent.QUIZ_ANSWER_SUBMISSION, analysis.getIntent());
    }

    @Test
    void gpaFollowUpContinuesSimulation() {
        AIConversationContextService.Context ctx = contextService.resolve(
                List.of(message("USER", "Phân tích GPA")), "Nếu thêm 15 tín chỉ thì sao?");
        AIIntentService.IntentAnalysis analysis =
                intentService.analyze("Nếu thêm 15 tín chỉ thì sao?", true, ctx.getPrior());
        assertEquals(AIIntentService.Intent.GPA_WHAT_IF, analysis.getIntent());
    }

    @Test
    void examFollowUpContinuesExamQuery() {
        AIConversationContextService.Context ctx = contextService.resolve(
                List.of(message("USER", "Hỏi lịch thi")), "Còn tuần sau?");
        AIIntentService.IntentAnalysis analysis =
                intentService.analyze("Còn tuần sau?", true, ctx.getPrior());
        assertEquals(AIIntentService.Intent.EXAM_SCHEDULE, analysis.getIntent());
    }

    @Test
    void topicSwitchIsDetectedWithRegex() {
        assertTrue(intentService.isTopicSwitch("GPA của tôi thì nếu học thêm 15 tín chỉ?"));
        assertFalse(intentService.isTopicSwitch("Giải thích khái niệm khóa ngoài"));
    }

    private AIMessage message(String role, String content) {
        return AIMessage.builder().role(role).content(content).build();
    }
}
