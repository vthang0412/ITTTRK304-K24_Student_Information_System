package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.AIInsightsDto;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.impl.AIInsightsServiceImpl;
import com.example.student_management.service.grade.GradebookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIInsightsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private GradebookService gradebookService;

    @Mock
    private AIContextService contextService;

    @Mock
    private AIProviderService providerService;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @InjectMocks
    private AIInsightsServiceImpl insightsService;

    private User studentUser;
    private Student student;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(insightsService, "rateLimitPerHour", 60);

        student = Student.builder()
                .id(1L)
                .studentCode("SV001")
                .fullName("Nguyễn Văn Sinh")
                .department("CNTT")
                .status(AccountStatus.ACTIVE)
                .build();

        studentUser = User.builder()
                .id(1L)
                .username("student")
                .role(Role.STUDENT)
                .student(student)
                .build();
        student.setUser(studentUser);
    }

    @Test
    void getInsights_shouldCalculateAccurateGpaCreditsSemesterTrendsAndLowScores() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(List.of());
        when(contextService.buildSystemContextPrompt(student, studentUser)).thenReturn("Context");
        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .providerUsed("SIS-ASSISTANT-ENGINE")
                        .modelUsed("sis-local-v1")
                        .success(false)
                        .build());

        List<StudentGradeDto> grades = List.of(
                StudentGradeDto.builder()
                        .courseCode("INT2204").courseName("Lập trình hướng đối tượng")
                        .credits(3).semesterName("Học kỳ 1")
                        .finalScore(new BigDecimal("8.60")).classification("Xuất sắc").passed(true).build(),
                StudentGradeDto.builder()
                        .courseCode("INT3507").courseName("Công nghệ phần mềm")
                        .credits(3).semesterName("Học kỳ 2")
                        .finalScore(new BigDecimal("5.79")).classification("Trung bình").passed(true).build()
        );
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(grades);

        AIInsightsDto result = insightsService.getInsights("student");

        assertNotNull(result);
        assertEquals(6, result.getCompletedCredits());
        assertEquals(2, result.getTotalCompletedCourses());
        assertEquals(0, result.getTotalFailedCourses());
        // (8.60 * 3 + 5.79 * 3) / 6 = 7.20 (7.195 rounded HALF_UP)
        assertEquals(new BigDecimal("7.20"), result.getGpa10());
        assertEquals(2, result.getSemesterInsights().size());
        assertEquals("FIRST", result.getSemesterInsights().get(0).getTrend());
        assertEquals("DOWN", result.getSemesterInsights().get(1).getTrend());
        assertEquals(1, result.getLowScoreCourses().size());
        assertEquals("INT3507", result.getLowScoreCourses().get(0).get("courseCode"));
        assertTrue(result.getRecommendations().stream().anyMatch(r -> "IMPROVEMENT".equals(r.getType())));
        assertTrue(result.getRecommendations().stream().anyMatch(r -> "WARNING".equals(r.getType())));
    }

    @Test
    void getInsights_whenNoPublishedGrades_shouldInformMissingData() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(List.of());
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(List.of());
        when(contextService.buildSystemContextPrompt(student, studentUser)).thenReturn("Context");
        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .providerUsed("SIS-ASSISTANT-ENGINE")
                        .modelUsed("sis-local-v1")
                        .success(false)
                        .build());

        AIInsightsDto result = insightsService.refreshInsights("student");

        assertEquals(0, result.getCompletedCredits());
        assertEquals(BigDecimal.ZERO, result.getGpa10());
        assertEquals("Chưa có dữ liệu", result.getAcademicClassification());
        assertTrue(result.getAiSummary().contains("chưa có dữ liệu bảng điểm chính thức"));
    }

    @Test
    void getInsights_whenNonStudentRole_shouldThrowForbiddenException() {
        User adminUser = User.builder().id(99L).username("admin").role(Role.ADMIN).build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertThrows(ForbiddenException.class, () -> insightsService.getInsights("admin"));
    }
}

