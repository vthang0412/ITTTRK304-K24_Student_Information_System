package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.*;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.ai.AIStudyPlan;
import com.example.student_management.entity.ai.AIStudyPlanItem;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIStudyPlanItemRepository;
import com.example.student_management.repository.ai.AIStudyPlanRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.impl.AIStudyPlanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIStudyPlanServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AIStudyPlanRepository studyPlanRepository;

    @Mock
    private AIStudyPlanItemRepository studyPlanItemRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AIProviderService providerService;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @InjectMocks
    private AIStudyPlanServiceImpl studyPlanService;

    private User studentUser;
    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(studyPlanService, "rateLimitPerHour", 60);

        student = Student.builder()
                .id(1L)
                .studentCode("SV001")
                .fullName("Nguyễn Văn Sinh")
                .status(AccountStatus.ACTIVE)
                .build();

        studentUser = User.builder()
                .id(1L)
                .username("student")
                .role(Role.STUDENT)
                .student(student)
                .build();
        student.setUser(studentUser);

        course = Course.builder()
                .id(1L)
                .courseCode("INT2204")
                .courseName("Lập trình hướng đối tượng")
                .build();
    }

    @Test
    void testCreateStudyPlan_AutoGenerateItems_Success() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));
        when(courseRepository.findAllById(anyList())).thenReturn(List.of(course));

        when(studyPlanRepository.save(any(AIStudyPlan.class))).thenAnswer(invocation -> {
            AIStudyPlan p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .success(true)
                        .content("Hãy phân bổ 25 phút học, 5 phút nghỉ theo phương pháp Pomodoro.")
                        .providerUsed("fallback")
                        .modelUsed("rule-based")
                        .build());

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(6);

        CreateStudyPlanRequestDto request = CreateStudyPlanRequestDto.builder()
                .title("Kế hoạch tuần 1")
                .goal("Ôn tập OOP")
                .startDate(start)
                .endDate(end)
                .focusCourseIds(List.of(1L))
                .targetHoursPerDay(2)
                .build();

        StudyPlanDto result = studyPlanService.createStudyPlan("student", request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Kế hoạch tuần 1", result.getTitle());
        assertEquals("SV001", result.getStudentCode());
        assertEquals(7, result.getTotalTasks());
        assertEquals(0, result.getCompletedTasks());
        assertEquals(0.0, result.getCompletionRate());
        verify(studyPlanItemRepository).saveAll(anyList());
    }

    @Test
    void testCreateStudyPlan_EndDateBeforeStartDate_ThrowsBadRequest() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        LocalDate start = LocalDate.now();
        LocalDate end = start.minusDays(2);

        CreateStudyPlanRequestDto request = CreateStudyPlanRequestDto.builder()
                .startDate(start)
                .endDate(end)
                .build();

        assertThrows(BadRequestException.class, () -> studyPlanService.createStudyPlan("student", request));
    }

    @Test
    void testGetStudentStudyPlans_Success() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        AIStudyPlan plan = AIStudyPlan.builder()
                .id(1L)
                .student(student)
                .title("Kế hoạch 1")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        when(studyPlanRepository.findByStudentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plan));

        List<StudyPlanDto> result = studyPlanService.getStudentStudyPlans("student");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Kế hoạch 1", result.get(0).getTitle());
    }

    @Test
    void testGetStudyPlanDetail_OwnershipCheck_ThrowsNotFoundWhenNotOwned() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));
        when(studyPlanRepository.findByIdAndStudentId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> studyPlanService.getStudyPlanDetail("student", 99L));
    }

    @Test
    void testUpdatePlanItem_StatusCompleted_UpdatesCompletionRate() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        AIStudyPlan plan = AIStudyPlan.builder()
                .id(10L)
                .student(student)
                .title("Kế hoạch kiểm tra")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .status("ACTIVE")
                .items(new ArrayList<>())
                .build();

        AIStudyPlanItem item1 = AIStudyPlanItem.builder()
                .id(101L)
                .studyPlan(plan)
                .title("Bài 1")
                .studyDate(LocalDate.now())
                .status("PENDING")
                .build();

        AIStudyPlanItem item2 = AIStudyPlanItem.builder()
                .id(102L)
                .studyPlan(plan)
                .title("Bài 2")
                .studyDate(LocalDate.now().plusDays(1))
                .status("PENDING")
                .build();

        plan.getItems().add(item1);
        plan.getItems().add(item2);

        when(studyPlanRepository.findByIdAndStudentId(10L, 1L)).thenReturn(Optional.of(plan));
        when(studyPlanItemRepository.findByIdAndStudyPlanId(101L, 10L)).thenReturn(Optional.of(item1));
        when(studyPlanItemRepository.save(any(AIStudyPlanItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStudyPlanItemRequestDto updateRequest = UpdateStudyPlanItemRequestDto.builder()
                .status("COMPLETED")
                .build();

        StudyPlanItemDto updated = studyPlanService.updatePlanItem("student", 10L, 101L, updateRequest);

        assertNotNull(updated);
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals("ACTIVE", plan.getStatus()); // 1/2 hoàn thành -> kế hoạch vẫn ACTIVE
    }

    @Test
    void testUpdatePlanItem_AllCompleted_AutoCompletesPlan() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        AIStudyPlan plan = AIStudyPlan.builder()
                .id(10L)
                .student(student)
                .title("Kế hoạch kiểm tra")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now())
                .status("ACTIVE")
                .items(new ArrayList<>())
                .build();

        AIStudyPlanItem item1 = AIStudyPlanItem.builder()
                .id(101L)
                .studyPlan(plan)
                .title("Bài duy nhất")
                .studyDate(LocalDate.now())
                .status("PENDING")
                .build();

        plan.getItems().add(item1);

        when(studyPlanRepository.findByIdAndStudentId(10L, 1L)).thenReturn(Optional.of(plan));
        when(studyPlanItemRepository.findByIdAndStudyPlanId(101L, 10L)).thenReturn(Optional.of(item1));
        when(studyPlanItemRepository.save(any(AIStudyPlanItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStudyPlanItemRequestDto updateRequest = UpdateStudyPlanItemRequestDto.builder()
                .status("COMPLETED")
                .build();

        StudyPlanItemDto updated = studyPlanService.updatePlanItem("student", 10L, 101L, updateRequest);

        assertNotNull(updated);
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals("COMPLETED", plan.getStatus()); // Tất cả đã xong -> tự động chuyển COMPLETED
        verify(studyPlanRepository).save(plan);
    }

    @Test
    void testDeleteStudyPlan_Success() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        AIStudyPlan plan = AIStudyPlan.builder()
                .id(10L)
                .student(student)
                .title("Kế hoạch cần xóa")
                .build();

        when(studyPlanRepository.findByIdAndStudentId(10L, 1L)).thenReturn(Optional.of(plan));

        studyPlanService.deleteStudyPlan("student", 10L);

        verify(studyPlanRepository).delete(plan);
    }

    @Test
    void testNonStudentRole_ThrowsForbidden() {
        User lecturer = User.builder()
                .id(2L)
                .username("lecturer")
                .role(Role.LECTURER)
                .build();
        when(userRepository.findByUsername("lecturer")).thenReturn(Optional.of(lecturer));

        assertThrows(ForbiddenException.class, () -> studyPlanService.getStudentStudyPlans("lecturer"));
    }
}

