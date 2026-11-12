package com.example.student_management.service.ai;

import com.example.student_management.dto.ai.CourseAdvisorRequestDto;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;
import com.example.student_management.dto.ai.CourseRecommendationItemDto;
import com.example.student_management.dto.grade.StudentGradeDto;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.ai.AIUsageLogRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.ai.impl.AICourseAdvisorServiceImpl;
import com.example.student_management.service.grade.GradebookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AICourseAdvisorServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSectionRepository courseSectionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private GradebookService gradebookService;

    @Mock
    private AIProviderService providerService;

    @Mock
    private AIUsageLogRepository usageLogRepository;

    @InjectMocks
    private AICourseAdvisorServiceImpl advisorService;

    private User studentUser;
    private Student student;
    private Course courseOop;
    private Course courseDsa;
    private Course courseAi;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(advisorService, "rateLimitPerHour", 60);

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

        courseOop = Course.builder()
                .id(1L)
                .courseCode("INT2204")
                .courseName("Lập trình hướng đối tượng")
                .credits(3)
                .status(AccountStatus.ACTIVE)
                .build();

        courseDsa = Course.builder()
                .id(2L)
                .courseCode("INT3401")
                .courseName("Cấu trúc dữ liệu và giải thuật")
                .credits(3)
                .status(AccountStatus.ACTIVE)
                .build();

        courseAi = Course.builder()
                .id(3L)
                .courseCode("INT3405")
                .courseName("Trí tuệ nhân tạo")
                .credits(3)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void testRecommendations_PrerequisitesMet_ShouldBeRecommended() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        // Sinh viên đã đậu môn OOP (INT2204)
        StudentGradeDto gradeOop = StudentGradeDto.builder()
                .courseCode("INT2204")
                .courseName("Lập trình hướng đối tượng")
                .credits(3)
                .finalScore(new BigDecimal("8.50"))
                .passed(true)
                .build();
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(List.of(gradeOop));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(Collections.emptyList());

        // Hệ thống có 2 môn: OOP và DSA
        when(courseRepository.findAll()).thenReturn(List.of(courseOop, courseDsa));

        // Lớp học phần mở cho DSA (INT3401)
        CourseSection sectionDsa = CourseSection.builder()
                .id(10L)
                .sectionCode("INT3401-01")
                .course(courseDsa)
                .status(CourseSectionStatus.OPEN)
                .capacity(50)
                .schedule("Thứ 3, 07:30 - 09:30, Phòng 201")
                .build();
        when(courseSectionRepository.findByCourseId(1L)).thenReturn(Collections.emptyList());
        when(courseSectionRepository.findByCourseId(2L)).thenReturn(List.of(sectionDsa));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(20L);

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder()
                        .success(true)
                        .content("Đề xuất bạn nên học môn Cấu trúc dữ liệu và giải thuật trong kỳ này.")
                        .providerUsed("fallback")
                        .modelUsed("rule-based")
                        .build());

        CourseAdvisorResponseDto response = advisorService.getCourseRecommendations("student");

        assertNotNull(response);
        assertEquals("SV001", response.getStudentCode());
        assertEquals(3, response.getAccumulatedCredits());

        // OOP đã hoàn thành -> notRecommended
        assertTrue(response.getNotRecommendedCourses().stream().anyMatch(c -> "INT2204".equals(c.getCourseCode()) && "ALREADY_COMPLETED".equals(c.getStatus())));

        // DSA đã thỏa mãn tiên quyết OOP -> recommended
        assertTrue(response.getRecommendedCourses().stream().anyMatch(c -> "INT3401".equals(c.getCourseCode()) && "RECOMMENDED".equals(c.getStatus())));
        assertNotNull(response.getDisclaimer());
    }

    @Test
    void testRecommendations_PrerequisitesNotMet_ShouldBeNotRecommended() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        // Sinh viên chưa học môn nào
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(Collections.emptyList());
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(Collections.emptyList());

        // DSA yêu cầu OOP
        when(courseRepository.findAll()).thenReturn(List.of(courseDsa));
        when(courseSectionRepository.findByCourseId(2L)).thenReturn(Collections.emptyList());

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder().success(false).build());

        CourseAdvisorResponseDto response = advisorService.getCourseRecommendations("student");

        assertNotNull(response);
        // DSA phải nằm trong notRecommended vì thiếu môn tiên quyết INT2204
        Optional<CourseRecommendationItemDto> dsaItem = response.getNotRecommendedCourses().stream()
                .filter(c -> "INT3401".equals(c.getCourseCode()))
                .findFirst();
        assertTrue(dsaItem.isPresent());
        assertEquals("PREREQUISITE_NOT_MET", dsaItem.get().getStatus());
        assertFalse(dsaItem.get().isPrerequisitesSatisfied());
        assertTrue(dsaItem.get().getMissingPrerequisites().contains("INT2204"));
    }

    @Test
    void testRecommendations_FailedCourse_ShouldBeMustRetakeWithHighPriority() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        // Sinh viên rớt OOP (finalScore < 4.00, passed = false)
        StudentGradeDto failedOop = StudentGradeDto.builder()
                .courseCode("INT2204")
                .courseName("Lập trình hướng đối tượng")
                .credits(3)
                .finalScore(new BigDecimal("3.00"))
                .passed(false)
                .build();
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(List.of(failedOop));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(Collections.emptyList());

        when(courseRepository.findAll()).thenReturn(List.of(courseOop));

        CourseSection sectionOop = CourseSection.builder()
                .id(1L)
                .sectionCode("INT2204-01")
                .course(courseOop)
                .status(CourseSectionStatus.OPEN)
                .capacity(40)
                .schedule("Thứ 2, 07:30 - 09:30, Phòng 101")
                .build();
        when(courseSectionRepository.findByCourseId(1L)).thenReturn(List.of(sectionOop));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(10L);

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder().success(false).build());

        CourseAdvisorResponseDto response = advisorService.getCourseRecommendations("student");

        assertNotNull(response);
        Optional<CourseRecommendationItemDto> oopItem = response.getRecommendedCourses().stream()
                .filter(c -> "INT2204".equals(c.getCourseCode()))
                .findFirst();
        assertTrue(oopItem.isPresent());
        assertEquals("MUST_RETAKE", oopItem.get().getStatus());
        assertEquals("HIGH", oopItem.get().getPriority());
    }

    @Test
    void testRecommendations_ScheduleConflict_ShouldBeNotRecommended() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        // Sinh viên đã đậu OOP
        StudentGradeDto gradeOop = StudentGradeDto.builder()
                .courseCode("INT2204")
                .credits(3)
                .passed(true)
                .build();
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(List.of(gradeOop));

        // Sinh viên đang học 1 lớp có lịch: Thứ 2, 08:00 - 10:00
        CourseSection currentSection = CourseSection.builder()
                .id(99L)
                .sectionCode("EXISTING-01")
                .schedule("Thứ 2, 08:00 - 10:00, Phòng A1")
                .build();
        Enrollment activeEnrollment = Enrollment.builder()
                .id(1L)
                .status(EnrollmentStatus.ENROLLED)
                .courseSection(currentSection)
                .build();
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(List.of(activeEnrollment));

        when(courseRepository.findAll()).thenReturn(List.of(courseDsa));

        // Lớp DSA mới có lịch: Thứ 2, 09:00 - 11:00 (Trùng khoảng 09:00 - 10:00)
        CourseSection sectionDsa = CourseSection.builder()
                .id(10L)
                .sectionCode("INT3401-01")
                .course(courseDsa)
                .status(CourseSectionStatus.OPEN)
                .capacity(50)
                .schedule("Thứ 2, 09:00 - 11:00, Phòng A2")
                .build();
        when(courseSectionRepository.findByCourseId(2L)).thenReturn(List.of(sectionDsa));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(5L);

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder().success(false).build());

        CourseAdvisorResponseDto response = advisorService.getCourseRecommendations("student");

        assertNotNull(response);
        // Vì lớp duy nhất bị trùng lịch nên DSA rơi vào notRecommended với trạng thái SCHEDULE_CONFLICT
        Optional<CourseRecommendationItemDto> dsaItem = response.getNotRecommendedCourses().stream()
                .filter(c -> "INT3401".equals(c.getCourseCode()))
                .findFirst();
        assertTrue(dsaItem.isPresent());
        assertEquals("SCHEDULE_CONFLICT", dsaItem.get().getStatus());
        assertTrue(dsaItem.get().getAvailableSections().get(0).isHasScheduleConflict());
    }

    @Test
    void testRecommendWithPreferences_CareerOrientation_BoostsPriority() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        // Sinh viên đã đậu CTDL&GT (INT3401) và Đại số tuyến tính (MAT1013)
        StudentGradeDto g1 = StudentGradeDto.builder().courseCode("INT3401").passed(true).build();
        StudentGradeDto g2 = StudentGradeDto.builder().courseCode("MAT1013").passed(true).build();
        when(gradebookService.getStudentPublishedGrades("student")).thenReturn(List.of(g1, g2));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(Collections.emptyList());

        when(courseRepository.findAll()).thenReturn(List.of(courseAi));

        CourseSection sectionAi = CourseSection.builder()
                .id(20L)
                .sectionCode("INT3405-01")
                .course(courseAi)
                .status(CourseSectionStatus.OPEN)
                .capacity(40)
                .schedule("Thứ 5, 13:00 - 15:00, Phòng 301")
                .build();
        when(courseSectionRepository.findByCourseId(3L)).thenReturn(List.of(sectionAi));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(20L, EnrollmentStatus.ENROLLED)).thenReturn(10L);

        when(providerService.generateExternalAnalysis(anyString(), anyString()))
                .thenReturn(AIProviderService.AIProviderResult.builder().success(false).build());

        CourseAdvisorRequestDto pref = CourseAdvisorRequestDto.builder()
                .careerOrientation("Trí tuệ nhân tạo (AI)")
                .targetCredits(18)
                .build();

        CourseAdvisorResponseDto response = advisorService.recommendCoursesWithPreferences("student", pref);

        assertNotNull(response);
        Optional<CourseRecommendationItemDto> aiItem = response.getRecommendedCourses().stream()
                .filter(c -> "INT3405".equals(c.getCourseCode()))
                .findFirst();
        assertTrue(aiItem.isPresent());
        assertEquals("HIGH", aiItem.get().getPriority());
    }

    @Test
    void testNonStudentRole_ShouldThrowForbidden() {
        User lecturerUser = User.builder()
                .id(2L)
                .username("lecturer")
                .role(Role.LECTURER)
                .build();
        when(userRepository.findByUsername("lecturer")).thenReturn(Optional.of(lecturerUser));

        assertThrows(ForbiddenException.class, () -> advisorService.getCourseRecommendations("lecturer"));
    }
}
