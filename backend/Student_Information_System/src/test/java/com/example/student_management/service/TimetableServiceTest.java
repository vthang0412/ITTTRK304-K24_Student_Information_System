package com.example.student_management.service;

import com.example.student_management.dto.timetable.TimetableWeekResponse;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.timetable.impl.TimetableServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimetableServiceTest {
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private CourseSectionScheduleRepository courseSectionScheduleRepository;
    @Mock
    private RecurringCourseScheduleRepository recurringCourseScheduleRepository;
    @Mock
    private SemesterRepository semesterRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TimetableServiceImpl timetableService;

    private Semester semester;
    private CourseSection section;

    @BeforeEach
    void setUp() {
        Student student = Student.builder().id(10L).build();
        User user = User.builder().username("student1").student(student).build();
        student.setUser(user);
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));

        semester = Semester.builder()
                .id(3L)
                .semesterCode("HK1")
                .semesterName("Học kỳ 1")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 1, 15))
                .build();
        section = CourseSection.builder()
                .id(20L)
                .sectionCode("CS101-01")
                .schedule("Thứ 2, 09:00 - 10:00, Phòng A1")
                .semester(semester)
                .course(Course.builder().courseCode("CS101").courseName("Lập trình").credits(3).build())
                .build();
    }

    @Test
    void usesMondayWeekAndPaginatesActualOccurrences() {
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .build()));
        when(courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(List.of(20L)))
                .thenReturn(List.of());

        TimetableWeekResponse result = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 1);

        assertEquals(LocalDate.of(2026, 11, 9), result.getWeekStart());
        assertEquals(LocalDate.of(2026, 11, 15), result.getWeekEnd());
        assertEquals(1, result.getTotalElements());
        assertEquals(LocalDate.of(2026, 11, 9), result.getItems().get(0).getDate());
        assertEquals("CS101", result.getItems().get(0).getCourseCode());
    }

    @Test
    void usesScheduleRevisionEffectiveOnEachClassDate() {
        section.setSchedule("Thứ 2, 11:00 - 12:00, Phòng Mới");
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .build()));
        when(courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(List.of(20L)))
                .thenReturn(List.of(
                        CourseSectionSchedule.builder()
                                .courseSection(section)
                                .schedule("Thứ 2, 09:00 - 10:00, Phòng Cũ")
                                .effectiveFrom(LocalDate.of(2026, 9, 1))
                                .effectiveTo(LocalDate.of(2026, 11, 10))
                                .build(),
                        CourseSectionSchedule.builder()
                                .courseSection(section)
                                .schedule("Thứ 2, 11:00 - 12:00, Phòng Mới")
                                .effectiveFrom(LocalDate.of(2026, 11, 11))
                                .effectiveTo(LocalDate.of(2027, 1, 15))
                                .build()));

        TimetableWeekResponse previousWeek = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10);
        TimetableWeekResponse result = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 19), 1, 10);

        assertEquals("09:00", previousWeek.getItems().get(0).getStartTime());
        assertEquals("Phòng Cũ", previousWeek.getItems().get(0).getRoom());
        assertEquals("11:00", result.getItems().get(0).getStartTime());
        assertEquals("Phòng Mới", result.getItems().get(0).getRoom());
    }

    @Test
    void paginatesAllOccurrencesWithoutDroppingTheTotal() {
        section.setSchedule("Thứ 2, 09:00 - 10:00, A1; Thứ 2, 11:00 - 12:00, A2");
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .build()));
        when(courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(List.of(20L)))
                .thenReturn(List.of());

        TimetableWeekResponse result = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 2, 1);

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(2, result.getPage());
        assertEquals(1, result.getItems().size());
        assertEquals("11:00", result.getItems().get(0).getStartTime());
    }

    @Test
    void onlyQueriesTimetableForAuthenticatedStudentsProfile() {
        Student otherStudent = Student.builder().id(11L).build();
        User otherUser = User.builder().username("student2").student(otherStudent).build();
        when(userRepository.findByUsername("student2")).thenReturn(Optional.of(otherUser));
        when(enrollmentRepository.findForTimetableBySemester(11L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of());

        TimetableWeekResponse result = timetableService.getWeek(
                "student2", 3L, LocalDate.of(2026, 11, 12), 1, 10);

        assertEquals(0, result.getTotalElements());
        verify(enrollmentRepository).findForTimetableBySemester(11L, EnrollmentStatus.ENROLLED, 3L);
    }

    @Test
    void reportsApprovedSectionsWithoutInventingScheduleOccurrences() {
        section.setSchedule(null);
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .build()));
        when(courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(List.of(20L)))
                .thenReturn(List.of());

        TimetableWeekResponse result = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10);

        assertEquals(0, result.getTotalElements());
        assertEquals(1, result.getUnscheduledSections().size());
        assertEquals("CS101-01", result.getUnscheduledSections().get(0).getSectionCode());
    }

    @Test
    void expandsRecurringSessionsOnlyOnMatchingDatesAndIncludesPeriodNumbers() {
        section.setSchedule(null);
        RecurringCourseSchedule rule = RecurringCourseSchedule.builder()
                .id(60L)
                .courseSection(section)
                .dayOfWeek(1)
                .startPeriod(1)
                .endPeriod(3)
                .startTime(java.time.LocalTime.of(7, 30))
                .endTime(java.time.LocalTime.of(10, 30))
                .classroom("A101")
                .startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 11, 30))
                .build();
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L)
                        .courseSection(section)
                        .status(EnrollmentStatus.ENROLLED)
                        .build()));
        when(recurringCourseScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(List.of(20L)))
                .thenReturn(List.of(rule));

        TimetableWeekResponse firstWeek = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 16), 1, 10);
        TimetableWeekResponse lastWeek = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 30), 1, 10);
        TimetableWeekResponse afterEnd = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 12, 7), 1, 10);

        assertEquals(1, firstWeek.getTotalElements());
        assertEquals(1, lastWeek.getTotalElements());
        assertEquals(0, afterEnd.getTotalElements());
        assertEquals(1, firstWeek.getItems().get(0).getStartPeriod());
        assertEquals(3, firstWeek.getItems().get(0).getEndPeriod());
        assertEquals("07:30", firstWeek.getItems().get(0).getStartTime());
        assertEquals("A101", firstWeek.getItems().get(0).getRoom());
    }

    @Test
    void expandsMultipleWeeklySessionsAndHonorsCancellationDate() {
        section.setSchedule(null);
        RecurringCourseSchedule monday = RecurringCourseSchedule.builder()
                .id(61L).courseSection(section).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(java.time.LocalTime.of(8, 0)).endTime(java.time.LocalTime.of(10, 0))
                .classroom("A101").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 15)).cancelledFrom(LocalDate.of(2026, 11, 20)).build();
        RecurringCourseSchedule thursday = RecurringCourseSchedule.builder()
                .id(62L).courseSection(section).dayOfWeek(4).startPeriod(3).endPeriod(4)
                .startTime(java.time.LocalTime.of(13, 0)).endTime(java.time.LocalTime.of(15, 0))
                .classroom("B202").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 15)).build();
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L).courseSection(section).status(EnrollmentStatus.ENROLLED).build()));
        when(recurringCourseScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(List.of(20L)))
                .thenReturn(List.of(monday, thursday));

        TimetableWeekResponse result = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 16), 1, 10);
        TimetableWeekResponse followingWeek = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 23), 1, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals(1, followingWeek.getTotalElements());
        assertEquals(4, followingWeek.getItems().get(0).getDayOfWeek());
    }

    @Test
    void expandsTwoSessionsForEachOfFourWeeks() {
        section.setSchedule(null);
        RecurringCourseSchedule monday = RecurringCourseSchedule.builder()
                .id(71L).courseSection(section).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(java.time.LocalTime.of(8, 0)).endTime(java.time.LocalTime.of(10, 0))
                .classroom("A101").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 10)).build();
        RecurringCourseSchedule thursday = RecurringCourseSchedule.builder()
                .id(72L).courseSection(section).dayOfWeek(4).startPeriod(3).endPeriod(4)
                .startTime(java.time.LocalTime.of(13, 0)).endTime(java.time.LocalTime.of(15, 0))
                .classroom("B202").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 10)).build();
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(4L).courseSection(section).status(EnrollmentStatus.ENROLLED).build()));
        when(recurringCourseScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(List.of(20L)))
                .thenReturn(List.of(monday, thursday));

        int total = 0;
        for (LocalDate mondayOfWeek = LocalDate.of(2026, 11, 16);
             !mondayOfWeek.isAfter(LocalDate.of(2026, 12, 7));
             mondayOfWeek = mondayOfWeek.plusWeeks(1)) {
            total += timetableService.getWeek("student1", 3L, mondayOfWeek, 1, 10).getTotalElements();
        }

        assertEquals(8, total);
    }

    @Test
    void adminWeeklyTimetableIncludesActualRecurringSessionsWithoutStudentEnrollment() {
        section.setSchedule(null);
        RecurringCourseSchedule rule = RecurringCourseSchedule.builder()
                .id(81L).courseSection(section).dayOfWeek(1).startPeriod(2).endPeriod(4)
                .startTime(java.time.LocalTime.of(9, 0)).endTime(java.time.LocalTime.of(12, 0))
                .classroom("A101").startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 12, 31)).build();
        when(courseSectionRepository.findForAdminTimetable(3L, null, null)).thenReturn(List.of(section));
        when(recurringCourseScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(List.of(20L)))
                .thenReturn(List.of(rule));

        TimetableWeekResponse result = timetableService.getAdminWeek(
                3L, null, null, "A101", LocalDate.of(2026, 11, 12), 1, 50);

        assertEquals(LocalDate.of(2026, 11, 9), result.getWeekStart());
        assertEquals(1, result.getTotalElements());
        assertEquals("CS101-01", result.getItems().get(0).getSectionCode());
        assertEquals(2, result.getItems().get(0).getStartPeriod());
        assertEquals("09:00", result.getItems().get(0).getStartTime());
        assertEquals("A101", result.getItems().get(0).getRoom());
        org.junit.jupiter.api.Assertions.assertNull(result.getItems().get(0).getEnrollmentId());
    }

    @Test
    void adminWeeklyTimetableExcludesCanceledRulesAndFiltersRoom() {
        section.setSchedule(null);
        RecurringCourseSchedule rule = RecurringCourseSchedule.builder()
                .id(82L).courseSection(section).dayOfWeek(1).startPeriod(2).endPeriod(4)
                .startTime(java.time.LocalTime.of(9, 0)).endTime(java.time.LocalTime.of(12, 0))
                .classroom("B202").startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 12, 31)).cancelledFrom(LocalDate.of(2026, 11, 9)).build();
        when(courseSectionRepository.findForAdminTimetable(null, null, null)).thenReturn(List.of(section));
        when(recurringCourseScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(List.of(20L)))
                .thenReturn(List.of(rule));

        TimetableWeekResponse result = timetableService.getAdminWeek(
                null, null, null, "B202", LocalDate.of(2026, 11, 12), 1, 50);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void reportsAnUnlinkedStudentProfileInsteadOfShowingAnEmptyTimetable() {
        when(userRepository.findByUsername("orphan")).thenReturn(Optional.of(
                User.builder().username("orphan").build()));

        assertThrows(BadRequestException.class, () -> timetableService.getWeek(
                "orphan", null, LocalDate.of(2026, 11, 12), 1, 10));
    }
}
