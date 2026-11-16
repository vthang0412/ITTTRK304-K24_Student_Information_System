package com.example.student_management.service;

import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.academic.RecurringCourseSchedule;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.academic.impl.EnrollmentServiceImpl;
import com.example.student_management.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentScheduleConflictTest {
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private CourseSectionScheduleRepository courseSectionScheduleRepository;
    @Mock
    private RecurringCourseScheduleRepository recurringCourseScheduleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    @Test
    void rejectsEnrollmentWhenEnrolledOrPendingClassOverlaps() {
        Semester semester = Semester.builder()
                .id(1L)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 1, 15))
                .status(SemesterStatus.REGISTRATION_OPEN)
                .build();
        Student student = Student.builder().id(10L).status(AccountStatus.ACTIVE).build();
        User user = User.builder().username("student1").student(student).build();
        CourseSection existing = CourseSection.builder()
                .id(2L)
                .sectionCode("CS101-01")
                .schedule("Thứ 2, 09:00 - 10:00, A1")
                .semester(semester)
                .build();
        CourseSection candidate = CourseSection.builder()
                .id(3L)
                .sectionCode("CS102-01")
                .schedule("Thứ 2, 09:30 - 10:30, B1")
                .semester(semester)
                .status(CourseSectionStatus.OPEN)
                .capacity(30)
                .build();

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(courseSectionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(candidate));
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(List.of(Enrollment.builder()
                .id(20L)
                .student(student)
                .courseSection(existing)
                .status(EnrollmentStatus.ENROLLED)
                .build()));

        assertThrows(ConflictException.class, () -> enrollmentService.enroll("student1", 3L));
    }

    @Test
    void rejectsEnrollmentWhenExistingApprovedClassUsesRecurringSchedule() {
        Semester semester = Semester.builder()
                .id(1L)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 1, 15))
                .status(SemesterStatus.REGISTRATION_OPEN)
                .build();
        Student student = Student.builder().id(10L).status(AccountStatus.ACTIVE).build();
        User user = User.builder().username("student1").student(student).build();
        CourseSection existing = CourseSection.builder()
                .id(2L).sectionCode("CS101-01")
                .course(Course.builder().id(2L).courseCode("CS101").courseName("Java").credits(3).build())
                .semester(semester).status(CourseSectionStatus.OPEN).build();
        CourseSection candidate = CourseSection.builder()
                .id(3L).sectionCode("CS102-01")
                .course(Course.builder().id(3L).courseCode("CS102").courseName("Web").credits(3).build())
                .semester(semester).schedule("Thứ 2, 09:30 - 10:30, B1")
                .status(CourseSectionStatus.OPEN).capacity(30).build();
        Enrollment currentEnrollment = Enrollment.builder()
                .id(20L).student(student).courseSection(existing).status(EnrollmentStatus.ENROLLED).build();
        RecurringCourseSchedule recurring = RecurringCourseSchedule.builder()
                .courseSection(existing).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(java.time.LocalTime.of(9, 0)).endTime(java.time.LocalTime.of(10, 0))
                .classroom("A1").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 15)).build();

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));
        when(courseSectionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(candidate));
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(List.of(currentEnrollment));
        when(courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(2L))
                .thenReturn(List.of());
        when(courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(3L))
                .thenReturn(List.of());
        when(recurringCourseScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(2L))
                .thenReturn(List.of(recurring));
        when(recurringCourseScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(3L))
                .thenReturn(List.of());

        assertThrows(ConflictException.class, () -> enrollmentService.enroll("student1", 3L));
    }
}
