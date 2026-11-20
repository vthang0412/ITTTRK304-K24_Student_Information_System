package com.example.student_management.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AITutorServiceTest {

    private final AITutorService service = new AITutorService();

    @Test
    void plansTutorTasksWithoutSisData() {
        assertEquals(AITutorService.TutorTask.EXPLANATION, service.plan("Giải thích khái niệm SQL", null).getTask());
        assertEquals(AITutorService.TutorTask.EXERCISE, service.plan("Cho tôi bài tập Java cơ bản", null).getTask());
        assertEquals(AITutorService.TutorTask.CODE_REVIEW, service.plan("Review code Java này", null).getTask());
        assertEquals(AITutorService.TutorTask.QUIZ, service.plan("Tạo quiz về cơ sở dữ liệu", null).getTask());
        assertEquals(AITutorService.TutorTask.ANSWER_EVALUATION, service.plan("Đáp án này đúng không?", null).getTask());
        assertEquals(AITutorService.TutorTask.STUDY_MATERIAL, service.plan("Tóm tắt tài liệu ôn tập", null).getTask());
    }

    @Test
    void distinguishesTutorAndSisQuestions() {
        assertTrue(service.isTutorQuestion("Giải thích thuật toán sắp xếp"));
        assertFalse(service.isTutorQuestion("GPA của tôi là bao nhiêu?"));
        assertTrue(service.mentionsSisData("Giải thích điểm GPA của tôi"));
    }

    @Test
    void fallbackDoesNotMentionPersonalSisNumbers() {
        AITutorService.TutorPlan plan = service.plan("Giải thích khái niệm khóa ngoài", null);
        String answer = service.buildFallback(plan, "Giải thích khái niệm khóa ngoài", null);

        assertTrue(answer.contains("AI Tutor"));
        assertTrue(answer.contains("không phải dữ liệu điểm/học vụ"));
        assertFalse(answer.contains("SV"));
        assertFalse(answer.contains("GPA"));
    }
}
