package com.example.student_management.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIIntentServiceTest {

    private final AIIntentService service = new AIIntentService();

    @Test
    void detectsPureTutorIntents() {
        assertEquals(AIIntentService.Intent.TUTOR_EXPLANATION, service.detectIntent("Giải thích khái niệm SQL", false));
        assertEquals(AIIntentService.Intent.TUTOR_EXERCISE, service.detectIntent("Cho tôi bài tập Java", false));
        assertEquals(AIIntentService.Intent.TUTOR_CODE_REVIEW, service.detectIntent("Review code Java này", false));
        assertEquals(AIIntentService.Intent.TUTOR_QUIZ, service.detectIntent("Tạo quiz cơ sở dữ liệu", false));
        assertEquals(AIIntentService.Intent.TUTOR_STUDY_MATERIAL, service.detectIntent("Tóm tắt tài liệu ôn tập", false));
    }

    @Test
    void detectsCombinedSisTutorIntent() {
        assertEquals(AIIntentService.Intent.COMBINED_SIS_TUTOR,
                service.detectIntent("Giải thích điểm GPA của tôi", false));
    }

    @Test
    void keepsSisIntents() {
        assertEquals(AIIntentService.Intent.GPA_QUERY, service.detectIntent("GPA của tôi là bao nhiêu?", false));
        assertEquals(AIIntentService.Intent.CREDIT_QUERY, service.detectIntent("Tôi đã tích lũy bao nhiêu tín chỉ?", false));
    }

    @Test
    void analysisAttachesTutorPlanForTutorIntents() {
        AIIntentService.IntentAnalysis analysis = service.analyze("Giải thích khái niệm khóa ngoài", false);

        assertEquals(AIIntentService.Intent.TUTOR_EXPLANATION, analysis.getIntent());
        assertTrue(analysis.getTutorPlan().getInstructions().contains("AI Tutor"));
        assertNull(service.analyze("GPA của tôi là bao nhiêu?", false).getTutorPlan());
    }
}
