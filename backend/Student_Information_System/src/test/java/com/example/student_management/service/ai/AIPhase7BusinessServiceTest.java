package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIRecommendationDto;
import com.example.student_management.dto.ai.AIInsightsDto;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;
import com.example.student_management.dto.ai.CourseRecommendationItemDto;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIPhase7BusinessServiceTest {

    @Mock private AIChatToolsService tools;

    private User user;
    private Student student;

    @BeforeEach
    void setUp() {
        student = Student.builder().id(10L).studentCode("SV24001").fullName("An").build();
        user = User.builder().id(1L).username("student_an").role(Role.STUDENT).student(student).build();
    }

    @Test
    void gpaWhatIfCalculatesFromRealGpaAndDoesNotMutateSources() {
        when(tools.getPublishedGrades(user)).thenReturn(List.of(
                grade("INT1001", 3, "8.00", true)));
        when(tools.computeGpaAndCredits(anyList())).thenReturn(Map.of("gpa4", new BigDecimal("3.00")));

        AIGpaWhatIfService.WhatIfResult result = new AIGpaWhatIfService().simulate(user,
                "Nếu GPA kỳ tới là 3.5 thì thêm 15 tín chỉ thì sao?", Map.of(), tools);

        assertTrue(result.isComplete());
        assertTrue(result.getAnswer().contains("3.42"), result.getAnswer());
        assertTrue(result.getAnswer().contains("không phải GPA thật"));
        assertTrue(result.getAnswer().contains("3.00 × 3"), result.getAnswer());
    }

    @Test
    void gpaWhatIfAsksForMissingCredits() {
        AIGpaWhatIfService.WhatIfResult result = new AIGpaWhatIfService().simulate(user,
                "Nếu GPA kỳ tới là 3.5 thì sao?", Map.of(), tools);
        assertFalse(result.isComplete());
        assertTrue(result.getAnswer().contains("bao nhiêu tín chỉ"));
    }

    @Test
    void gpaWhatIfWarnsAboutDuplicateAndFailedCourses() {
        when(tools.getPublishedGrades(user)).thenReturn(List.of(
                grade("INT1001", 3, "3.50", false),
                grade("INT1001", 3, "7.00", true)));
        when(tools.computeGpaAndCredits(anyList())).thenReturn(Map.of("gpa4", new BigDecimal("2.00")));

        String answer = new AIGpaWhatIfService().simulate(user,
                "Nếu GPA kỳ tới là 3.0 thì thêm 12 tín chỉ thì sao?", Map.of(), tools).getAnswer();

        assertTrue(answer.contains("trùng mã môn"), answer);
        assertTrue(answer.contains("chưa đạt"), answer);
    }

    @Test
    void quizHidesAnswersThenGradesOnlyActiveConversationQuiz() {
        AIQuizService quiz = new AIQuizService();
        String created = quiz.start("SV24001", 100L, "Java Core", "Tạo 3 câu trắc nghiệm Java", 3);

        assertTrue(created.contains("Chưa hiển thị đáp án đúng"), created);
        assertFalse(created.contains("Đáp án đúng:"));
        assertTrue(created.contains("1."));

        String graded = quiz.grade("SV24001", 100L, "1. A, 2. A, 3. A");
        assertTrue(graded.contains("3/3"), graded);
        assertTrue(graded.contains("Giải thích:"), graded);
        assertTrue(graded.contains("không phải điểm học vụ SIS"), graded);
        assertTrue(quiz.grade("SV24001", 100L, "1. A").contains("không tìm thấy quiz"));
        assertTrue(quiz.grade("SV24001", 999L, "1. A").contains("không tìm thấy quiz"));
    }

    @Test
    void quizRejectsUnsupportedTopicAndHandlesWrongAnswers() {
        AIQuizService quiz = new AIQuizService();
        assertTrue(quiz.start("SV24001", 100L, "Lịch sử", "Tạo quiz lịch sử", 3).contains("Java Core"));
        quiz.start("SV24001", 101L, "SQL", "Tạo 2 câu SQL", 2);
        String graded = quiz.grade("SV24001", 101L, "1. D, 2. D");
        assertTrue(graded.contains("0/2"), graded);
        assertTrue(graded.contains("Câu 1:** Sai"), graded);
        assertTrue(graded.contains("Chủ đề cần ôn lại"), graded);
    }

    @Test
    void timetableDetectsClassAndExamDataWithoutInventingDates() {
        when(tools.getAllEnrollments(student)).thenReturn(List.of());
        when(tools.summarizeEnrollments(anyList())).thenReturn(Map.of("items", List.of(
                Map.of("status", "ENROLLED", "courseName", "Java", "sectionCode", "SEC1",
                        "schedule", "Thứ 2, 07:30 - 09:30, Phòng A1"),
                Map.of("status", "ENROLLED", "courseName", "SQL", "sectionCode", "SEC2",
                        "schedule", "Thứ 2, 08:00 - 10:00, Phòng A2"))));
        when(tools.getUpcomingExams(student)).thenReturn(List.of(Map.of(
                "examDate", "2026-12-01", "startTime", "09:00", "endTime", "11:00",
                "courseName", "Java", "room", "P101", "status", "SCHEDULED")));

        String answer = new AITimetableAssistantService().build("SV24001", student, tools, true);

        assertTrue(answer.contains("Lịch học (lịch lặp theo hệ thống)"), answer);
        assertTrue(answer.contains("Lịch thi (theo ngày cụ thể)"), answer);
        assertTrue(answer.contains("Trùng lịch học"), answer);
        assertFalse(answer.contains("| 01/12 |"), answer);
    }

    @Test
    void studyPlanUsesLowScorePriorityAndStatesItIsNotSaved() {
        when(tools.getPublishedGrades(user)).thenReturn(List.of(
                grade("INT1001", 3, "3.50", false),
                grade("INT1002", 3, "9.00", true)));
        when(tools.getUpcomingExams(student)).thenReturn(List.of(Map.of("examDate", "2026-12-05")));
        when(tools.getAllEnrollments(student)).thenReturn(List.of());
        when(tools.summarizeEnrollments(anyList())).thenReturn(Map.of("items", List.of()));

        String answer = new AIStudyPlanChatService().build(user, "Tạo kế hoạch học tập 14 ngày", tools);

        assertTrue(answer.contains("14 ngày"), answer);
        assertTrue(answer.contains("INT1001"), answer);
        assertTrue(answer.contains("kiến thức nền tảng"), answer);
        assertTrue(answer.contains("chưa được lưu"), answer);
        assertTrue(answer.contains("không khẳng định khung này hoàn toàn trống"), answer);
    }

    @Test
    void formattersUseStructuredDataInsteadOfLlmText() {
        CourseAdvisorResponseDto advisor = CourseAdvisorResponseDto.builder()
                .accumulatedCredits(45).activeCredits(12).maxCreditsPerSemester(24)
                .recommendedCourses(List.of(CourseRecommendationItemDto.builder()
                        .courseCode("INT3401").courseName("CTDL").credits(4)
                        .priority("HIGH").status("RECOMMENDED").reason("Đủ điều kiện").build()))
                .notRecommendedCourses(List.of())
                .disclaimer("Chỉ tham khảo").build();
        String advisorText = AIBusinessChatFormatter.formatAdvisor(advisor);
        assertTrue(advisorText.contains("45 TC"), advisorText);
        assertTrue(advisorText.contains("INT3401"), advisorText);
        assertTrue(advisorText.contains("Chỉ tham khảo"), advisorText);

        AIInsightsDto insights = AIInsightsDto.builder()
                .gpa10(new BigDecimal("7.50")).gpa4(new BigDecimal("3.00"))
                .completedCredits(45).activeCredits(12)
                .totalCompletedCourses(15).totalFailedCourses(2)
                .recommendations(List.of(AIRecommendationDto.builder()
                        .priority("HIGH").title("Cải thiện INT1001").description("Ôn lại nền tảng").build()))
                .build();
        String insightText = AIBusinessChatFormatter.formatInsights(insights);
        assertTrue(insightText.contains("7.50"), insightText);
        assertTrue(insightText.contains("Cải thiện INT1001"), insightText);
    }

    private StudentGradeDto grade(String code, int credits, String score, boolean passed) {
        return StudentGradeDto.builder()
                .courseCode(code).courseName(code).credits(credits)
                .finalScore(new BigDecimal(score)).passed(passed)
                .semesterName("HK1").build();
    }
}
