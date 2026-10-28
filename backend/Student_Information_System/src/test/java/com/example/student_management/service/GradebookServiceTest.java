package com.example.student_management.service;

import com.example.student_management.dto.request.GradeComponentConfigRequest;
import com.example.student_management.dto.request.GradeComponentRequest;
import com.example.student_management.dto.request.GradeRequest;
import com.example.student_management.entity.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradebookServiceTest {

    @Mock private GradebookRepository gradebookRepository;
    @Mock private GradeComponentRepository gradeComponentRepository;
    @Mock private GradeComponentScoreRepository gradeComponentScoreRepository;
    @Mock private GradeAuditLogRepository gradeAuditLogRepository;
    @Mock private CourseSectionRepository courseSectionRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private GradebookService service;

    private User admin() {
        return User.builder().id(1L).username("admin").role(Role.ADMIN).build();
    }

    private CourseSection section() {
        return CourseSection.builder()
                .id(10L)
                .sectionCode("INT2204-01")
                .lecturer(Lecturer.builder().id(7L).fullName("Giảng viên").build())
                .status(CourseSectionStatus.OPEN)
                .build();
    }

    @Test
    void updateComponentsRejectsWeightTotalOtherThan100() {
        CourseSection section = section();
        Gradebook gradebook = Gradebook.builder()
                .id(100L)
                .courseSection(section)
                .status(GradebookStatus.OPEN)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(gradebookRepository.findByCourseSectionId(10L)).thenReturn(Optional.of(gradebook));
        when(gradeComponentScoreRepository.countByGradeComponentGradebookId(100L)).thenReturn(0L);

        GradeComponentRequest first = new GradeComponentRequest();
        first.setComponentName("Giữa kỳ");
        first.setWeight(new BigDecimal("40"));
        GradeComponentRequest second = new GradeComponentRequest();
        second.setComponentName("Cuối kỳ");
        second.setWeight(new BigDecimal("50"));
        GradeComponentConfigRequest request = new GradeComponentConfigRequest();
        request.setComponents(List.of(first, second));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.updateComponents(10L, request, "admin"));
        assertTrue(error.getMessage().contains("100"));
        verify(gradeComponentRepository, never()).save(any());
    }

    @Test
    void saveGradeRejectsWhenGradebookIsNotOpen() {
        CourseSection section = section();
        Enrollment enrollment = Enrollment.builder()
                .id(200L)
                .courseSection(section)
                .status(EnrollmentStatus.ENROLLED)
                .build();
        Gradebook gradebook = Gradebook.builder()
                .id(100L)
                .courseSection(section)
                .status(GradebookStatus.LOCKED)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(enrollmentRepository.findById(200L)).thenReturn(Optional.of(enrollment));
        when(gradebookRepository.findByCourseSectionId(10L)).thenReturn(Optional.of(gradebook));

        GradeRequest request = new GradeRequest();
        request.setFinalScore(new BigDecimal("8"));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.saveGrade(200L, request, "admin"));
        assertTrue(error.getMessage().toLowerCase().contains("gradebook"));
        verify(gradeRepository, never()).save(any());
    }

    @Test
    void saveGradeCalculatesFinalScoreFromComponentScores() {
        CourseSection section = section();
        Enrollment enrollment = Enrollment.builder()
                .id(200L)
                .courseSection(section)
                .status(EnrollmentStatus.ENROLLED)
                .build();
        Gradebook gradebook = Gradebook.builder()
                .id(100L)
                .courseSection(section)
                .status(GradebookStatus.OPEN)
                .build();
        GradeComponent midterm = GradeComponent.builder()
                .id(1L).gradebook(gradebook).componentName("Giữa kỳ")
                .weight(new BigDecimal("30")).maxScore(new BigDecimal("10"))
                .displayOrder(1).required(true).build();
        GradeComponent finalExam = GradeComponent.builder()
                .id(2L).gradebook(gradebook).componentName("Cuối kỳ")
                .weight(new BigDecimal("70")).maxScore(new BigDecimal("10"))
                .displayOrder(2).required(true).build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(enrollmentRepository.findById(200L)).thenReturn(Optional.of(enrollment));
        when(gradebookRepository.findByCourseSectionId(10L)).thenReturn(Optional.of(gradebook));
        when(gradeComponentRepository.findByGradebookIdOrderByDisplayOrderAscIdAsc(100L))
                .thenReturn(List.of(midterm, finalExam));
        when(gradeRepository.findByEnrollmentId(200L)).thenReturn(Optional.empty());
        when(gradeComponentScoreRepository.findByGradeComponentIdAndEnrollmentId(anyLong(), eq(200L)))
                .thenReturn(Optional.empty());
        when(gradeComponentScoreRepository.save(any(GradeComponentScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gradeComponentScoreRepository.findByEnrollmentId(200L)).thenReturn(List.of(
                GradeComponentScore.builder().gradeComponent(midterm).enrollment(enrollment)
                        .score(new BigDecimal("8.00")).build(),
                GradeComponentScore.builder().gradeComponent(finalExam).enrollment(enrollment)
                        .score(new BigDecimal("9.00")).build()
        ));
        when(gradeRepository.save(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GradeRequest request = new GradeRequest();
        request.setFinalScore(new BigDecimal("10"));
        request.setComponentScores(Map.of(1L, new BigDecimal("8"), 2L, new BigDecimal("9")));

        Grade result = service.saveGrade(200L, request, "admin");

        assertEquals(new BigDecimal("8.70"), result.getFinalScore());
        assertEquals("admin", result.getUpdatedBy());
        verify(gradeRepository).save(any(Grade.class));
        verify(gradeAuditLogRepository, atLeast(3)).save(any(GradeAuditLog.class));
    }
}
