package com.example.student_management.controller;

import com.example.student_management.controller.academic.CourseController;
import com.example.student_management.controller.academic.CourseSectionController;
import com.example.student_management.controller.academic.SemesterController;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.repository.academic.CourseRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import com.example.student_management.repository.grade.GradebookRepository;
import com.example.student_management.entity.auth.Lecturer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDeleteControllerTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private SemesterRepository semesterRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private GradebookRepository gradebookRepository;
    @Mock
    private CourseSectionScheduleRepository courseSectionScheduleRepository;
    @Mock
    private RecurringCourseScheduleRepository recurringCourseScheduleRepository;

    @InjectMocks
    private CourseController courseController;
    @InjectMocks
    private SemesterController semesterController;
    @InjectMocks
    private CourseSectionController courseSectionController;

    @Test
    void courseDeleteRejectsWhenCourseHasLinkedSections() {
        Course course = Course.builder().id(10L).courseCode("CS101").courseName("Lập trình").credits(3)
                .status(AccountStatus.ACTIVE).build();
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.existsByCourseId(10L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseController.delete(10L));

        assertTrue(ex.getMessage().contains("học phần"));
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void semesterDeleteRejectsWhenSemesterHasLinkedSections() {
        Semester semester = Semester.builder().id(20L).semesterCode("20251").semesterName("Học kỳ 1")
                .status(SemesterStatus.REGISTRATION_OPEN).build();
        when(semesterRepository.findById(20L)).thenReturn(Optional.of(semester));
        when(courseSectionRepository.existsBySemesterId(20L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> semesterController.delete(20L));

        assertTrue(ex.getMessage().contains("học phần"));
        verify(semesterRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenSectionHasEnrollments() {
        CourseSection section = CourseSection.builder().id(30L).sectionCode("CS101-01").capacity(40)
                .status(CourseSectionStatus.OPEN).build();
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));
        when(enrollmentRepository.existsByCourseSectionId(30L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("sinh viên"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseDeleteAllowsUnusedCourse() {
        Course course = Course.builder().id(11L).courseCode("CS102").courseName("Cơ sở dữ liệu").credits(3)
                .status(AccountStatus.ACTIVE).build();
        when(courseRepository.findById(11L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.existsByCourseId(11L)).thenReturn(false);

        courseController.delete(11L);

        verify(courseRepository).delete(course);
    }

    @Test
    void semesterDeleteRejectsWhenSemesterIsInProgressEvenWithoutSections() {
        Semester semester = Semester.builder().id(21L).semesterCode("20262").semesterName("Học kỳ hiện tại")
                .status(SemesterStatus.IN_PROGRESS).build();
        when(semesterRepository.findById(21L)).thenReturn(Optional.of(semester));
        when(courseSectionRepository.existsBySemesterId(21L)).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> semesterController.delete(21L));

        assertTrue(ex.getMessage().contains("đang diễn ra"));
        verify(semesterRepository, never()).delete(any());
    }

    @Test
    void semesterDeleteAllowsUnusedCompletedSemester() {
        Semester semester = Semester.builder().id(22L).semesterCode("20251").semesterName("Học kỳ cũ")
                .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 5, 31))
                .status(SemesterStatus.COMPLETED).build();
        when(semesterRepository.findById(22L)).thenReturn(Optional.of(semester));
        when(courseSectionRepository.existsBySemesterId(22L)).thenReturn(false);

        semesterController.delete(22L);

        verify(semesterRepository).delete(semester);
    }

    @Test
    void courseSectionDeleteRejectsWhenLecturerIsAssigned() {
        CourseSection section = deletableSection();
        section.setLecturer(Lecturer.builder().id(7L).build());
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("giảng viên"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenRecurringScheduleExists() {
        CourseSection section = deletableSection();
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));
        when(recurringCourseScheduleRepository.existsByCourseSectionId(30L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("lịch học"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenScheduleHistoryExists() {
        CourseSection section = deletableSection();
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));
        when(courseSectionScheduleRepository.existsByCourseSectionId(30L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("lịch sử thay đổi lịch"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenGradebookExists() {
        CourseSection section = deletableSection();
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));
        when(gradebookRepository.findByCourseSectionId(30L)).thenReturn(Optional.of(
                com.example.student_management.entity.grade.Gradebook.builder()
                        .id(5L).courseSection(section).status(com.example.student_management.entity.grade.GradebookStatus.OPEN).build()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("bảng điểm"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteRejectsWhenRegistrationIsOpen() {
        CourseSection section = deletableSection();
        section.setStatus(CourseSectionStatus.OPEN);
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> courseSectionController.delete(30L));

        assertTrue(ex.getMessage().contains("mở đăng ký"));
        verify(courseSectionRepository, never()).delete(any());
    }

    @Test
    void courseSectionDeleteAllowsUnusedDraftSection() {
        CourseSection section = deletableSection();
        when(courseSectionRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(section));

        courseSectionController.delete(30L);

        verify(courseSectionRepository).delete(section);
    }

    @Test
    void updatingSectionSchedulePreservesFutureScheduleHistory() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate effectiveFrom = today.plusDays(1);
        Semester semester = Semester.builder().id(1L).semesterCode("20262").semesterName("Học kỳ mới")
                .startDate(today).endDate(today.plusMonths(4)).status(SemesterStatus.PLANNED).build();
        CourseSection section = CourseSection.builder().id(30L).sectionCode("CS101-01").capacity(40)
                .semester(semester).schedule("Lịch cũ").status(CourseSectionStatus.DRAFT).build();
        CourseSectionSchedule futureRevision = CourseSectionSchedule.builder()
                .id(1L).courseSection(section).schedule("Lịch đã lên kế hoạch")
                .effectiveFrom(effectiveFrom.plusDays(7)).effectiveTo(semester.getEndDate()).build();
        CourseSection update = CourseSection.builder()
                .schedule("Lịch mới").scheduleEffectiveFrom(effectiveFrom).build();
        when(courseSectionRepository.findById(30L)).thenReturn(Optional.of(section));
        when(courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(30L))
                .thenReturn(List.of(futureRevision));
        when(courseSectionRepository.save(section)).thenReturn(section);
        when(courseSectionScheduleRepository.save(any(CourseSectionSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        courseSectionController.update(30L, update);

        assertEquals(effectiveFrom, futureRevision.getCancelledFrom());
        verify(courseSectionScheduleRepository, never()).delete(any());
    }

    private CourseSection deletableSection() {
        return CourseSection.builder().id(30L).sectionCode("CS101-01").capacity(40)
                .status(CourseSectionStatus.DRAFT).build();
    }
}
