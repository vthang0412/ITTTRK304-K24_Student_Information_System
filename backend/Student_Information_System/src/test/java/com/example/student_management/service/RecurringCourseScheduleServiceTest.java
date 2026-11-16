package com.example.student_management.service;

import com.example.student_management.dto.academic.RecurringScheduleRequest;
import com.example.student_management.dto.academic.RecurringScheduleSessionRequest;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.service.academic.impl.RecurringCourseScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecurringCourseScheduleServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private CourseSectionRepository sectionRepository;
    @Mock
    private RecurringCourseScheduleRepository recurringScheduleRepository;
    @Mock
    private CourseSectionScheduleRepository scheduleHistoryRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private RecurringCourseScheduleServiceImpl scheduleService;

    private CourseSection target;
    private CourseSection other;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        start = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        end = start.plusWeeks(6);
        Semester semester = Semester.builder()
                .id(1L)
                .startDate(LocalDate.now(BUSINESS_ZONE).minusMonths(1))
                .endDate(end.plusWeeks(2))
                .build();
        target = section(1L, "JAVA01", "Java", semester, 10L);
        other = section(2L, "DB01", "Database", semester, 20L);

        when(sectionRepository.findById(1L)).thenReturn(Optional.of(target));
        when(sectionRepository.findAll()).thenReturn(List.of(target, other));
        when(enrollmentRepository.findByCourseSectionId(1L)).thenReturn(List.of());
        when(enrollmentRepository.findByCourseSectionIdInAndStatus(anyCollection(), eq(EnrollmentStatus.ENROLLED)))
                .thenReturn(List.of());
        when(recurringScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(anyCollection()))
                .thenReturn(List.of());
        when(scheduleHistoryRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    void allowsSameWeekdayWhenTimesDoNotOverlap() {
        RecurringScheduleRequest request = request(start, end,
                session(1, 1, 3, "08:00", "10:00", "A101"),
                session(1, 4, 6, "10:00", "12:00", "A101"));

        scheduleService.check(1L, request);
    }

    @Test
    void allowsSameTimeOnDifferentWeekdays() {
        scheduleService.check(1L, request(start, end,
                session(1, 1, 2, "08:00", "10:00", "A101"),
                session(3, 1, 2, "08:00", "10:00", "A101")));
    }

    @Test
    void rejectsOverlappingRoomUseOnlyWhenARealWeekdayOccurrenceOverlaps() {
        setOtherRecurringRule(1, 2, 4, "09:00", "11:00", "A101", start, end);

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> scheduleService.check(1L, request(start, end,
                        session(1, 1, 3, "08:00", "10:00", "A101"))));

        org.junit.jupiter.api.Assertions.assertTrue(conflict.getMessage().contains("Database"));
        org.junit.jupiter.api.Assertions.assertTrue(conflict.getMessage().contains("Thứ 2"));
    }

    @Test
    void allowsSameWeekdayAndTimeWhenTheirDateRangesContainNoSharedOccurrence() {
        LocalDate nextMonday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(
                java.time.DayOfWeek.MONDAY));
        LocalDate laterMonday = nextMonday.plusWeeks(3);
        setOtherRecurringRule(1, 1, 2, "09:00", "11:00", "A101", nextMonday, nextMonday.plusWeeks(1));

        scheduleService.check(1L, request(laterMonday, laterMonday.plusWeeks(1),
                session(1, 2, 3, "10:00", "12:00", "B202")));
    }

    @Test
    void allowsOverlappingCalendarRangesWhenTheyContainNoSharedWeekdayMeeting() {
        LocalDate tuesday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(
                java.time.DayOfWeek.TUESDAY));
        setOtherRecurringRule(1, 1, 2, "09:00", "11:00", "A101", tuesday, tuesday.plusDays(1));

        scheduleService.check(1L, request(tuesday, tuesday.plusDays(1),
                session(1, 2, 3, "10:00", "12:00", "B202")));
    }

    @Test
    void rejectsLecturerConflictEvenWhenRoomDiffers() {
        other.setLecturer(target.getLecturer());
        setOtherRecurringRule(1, 2, 4, "09:00", "11:00", "B202", start, end);

        assertThrows(ConflictException.class, () -> scheduleService.check(1L, request(start, end,
                session(1, 1, 3, "08:00", "10:00", "A101"))));
    }

    @Test
    void rejectsConflictForStudentEnrolledInBothSections() {
        Student student = Student.builder().id(55L).build();
        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .courseSection(other)
                .status(EnrollmentStatus.ENROLLED)
                .build();
        when(enrollmentRepository.findByCourseSectionId(1L)).thenReturn(List.of(enrollment));
        when(enrollmentRepository.findByCourseSectionIdInAndStatus(anyCollection(), eq(EnrollmentStatus.ENROLLED)))
                .thenReturn(List.of(enrollment));
        setOtherRecurringRule(1, 2, 4, "09:00", "11:00", "B202", start, end);

        assertThrows(ConflictException.class, () -> scheduleService.check(1L, request(start, end,
                session(1, 1, 3, "08:00", "10:00", "A101"))));
    }

    @Test
    void checksConflictsAgainstExistingLegacyFreeTextSchedules() {
        LocalDate nextMonday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(
                java.time.DayOfWeek.MONDAY));
        CourseSectionSchedule revision = CourseSectionSchedule.builder()
                .courseSection(other)
                .schedule("Thứ 2, 09:00 - 11:00, A101")
                .effectiveFrom(nextMonday)
                .effectiveTo(end)
                .build();
        when(scheduleHistoryRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(anyCollection()))
                .thenReturn(List.of(revision));

        assertThrows(ConflictException.class, () -> scheduleService.check(1L,
                request(nextMonday, end, session(1, 2, 3, "09:30", "10:30", "A101"))));
    }

    @Test
    void validatesDateTimeAndPeriodRanges() {
        assertThrows(BadRequestException.class, () -> scheduleService.check(1L,
                request(end, start, session(1, 1, 1, "08:00", "10:00", "A101"))));
        assertThrows(BadRequestException.class, () -> scheduleService.check(1L,
                request(start, end, session(1, 3, 2, "08:00", "10:00", "A101"))));
        assertThrows(BadRequestException.class, () -> scheduleService.check(1L,
                request(start, end, session(1, 3, 3, "08:00", "10:00", "A101"))));
        assertThrows(BadRequestException.class, () -> scheduleService.check(1L,
                request(start, end, session(1, 1, 2, "10:00", "10:00", "A101"))));
    }

    @Test
    void checksForRoomAndLecturerOverlapsOnActualDatesBeforeSaving() {
        LocalDate tuesday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(
                java.time.DayOfWeek.TUESDAY));
        setOtherRecurringRule(1, 2, 4, "09:00", "11:00", "A101", tuesday, tuesday);
        scheduleService.check(1L, request(tuesday, tuesday,
                session(1, 1, 2, "09:30", "10:30", "B202")));

        LocalDate monday = tuesday.with(java.time.temporal.TemporalAdjusters.next(
                java.time.DayOfWeek.MONDAY));
        setOtherRecurringRule(1, 2, 4, "09:00", "11:00", "A101", monday, monday);
        assertThrows(ConflictException.class, () -> scheduleService.check(1L, request(monday, monday,
                session(1, 1, 2, "09:30", "10:30", "A101"))));
    }

    @Test
    void replacesFutureRulesWithoutDeletingPastScheduleRecords() {
        RecurringCourseSchedule oldRule = RecurringCourseSchedule.builder()
                .id(90L).courseSection(target).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).classroom("A101")
                .startDate(start).endDate(end).build();
        target.setSchedule("Thứ 2, 08:00 - 10:00, A101");
        when(sectionRepository.findAllForUpdate()).thenReturn(List.of(target, other));
        when(recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(oldRule));
        when(scheduleHistoryRepository.findByCourseSectionIdOrderByEffectiveFromAsc(1L)).thenReturn(List.of());
        when(recurringScheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<com.example.student_management.dto.academic.RecurringScheduleSessionResponse> result =
                scheduleService.replace(1L, request(start.plusWeeks(1), end,
                        session(2, 3, 4, "13:00", "15:00", "B202")));

        assertEquals(1, result.size());
        assertEquals(start.plusWeeks(1), oldRule.getCancelledFrom());
        verify(scheduleHistoryRepository).save(any(CourseSectionSchedule.class));
        verify(recurringScheduleRepository, never()).delete(any());
    }

    @Test
    void deletionCancelsFutureMeetingsWithoutDeletingTheRecurringRule() {
        target.setSchedule("Thứ 2, 08:00 - 10:00, A101");
        RecurringCourseSchedule active = RecurringCourseSchedule.builder()
                .id(91L).courseSection(target).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).classroom("A101")
                .startDate(start).endDate(end).build();
        when(recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(active));
        when(sectionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));

        scheduleService.cancelActiveSessions(1L);

        assertEquals(LocalDate.now(BUSINESS_ZONE).plusDays(1), active.getCancelledFrom());
        assertEquals("", target.getSchedule());
        verify(scheduleHistoryRepository).save(any(CourseSectionSchedule.class));
        verify(recurringScheduleRepository, never()).delete(any());
    }

    @Test
    void showsRulesThatRemainActiveUntilAFutureCancellationDate() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        RecurringCourseSchedule activeUntilLater = RecurringCourseSchedule.builder()
                .id(92L).courseSection(target).dayOfWeek(1).startPeriod(1).endPeriod(2)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).classroom("A101")
                .startDate(today.minusWeeks(1)).endDate(today.plusWeeks(3))
                .cancelledFrom(today.plusWeeks(1)).build();
        RecurringCourseSchedule alreadyEnded = RecurringCourseSchedule.builder()
                .id(93L).courseSection(target).dayOfWeek(2).startPeriod(1).endPeriod(2)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).classroom("A101")
                .startDate(today.minusWeeks(3)).endDate(today.minusDays(1)).build();
        when(recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(1L))
                .thenReturn(List.of(activeUntilLater, alreadyEnded));

        List<com.example.student_management.dto.academic.RecurringScheduleSessionResponse> result =
                scheduleService.getActiveSessions(1L);

        assertEquals(1, result.size());
        assertEquals(activeUntilLater.getId(), result.get(0).getId());
    }

    private CourseSection section(Long id, String code, String courseName, Semester semester, Long lecturerId) {
        return CourseSection.builder()
                .id(id)
                .sectionCode(code)
                .course(Course.builder().id(id).courseName(courseName).build())
                .semester(semester)
                .lecturer(Lecturer.builder().id(lecturerId).fullName("Giảng viên " + lecturerId).build())
                .status(CourseSectionStatus.OPEN)
                .build();
    }

    private RecurringScheduleRequest request(LocalDate from, LocalDate to,
                                             RecurringScheduleSessionRequest... sessions) {
        RecurringScheduleRequest request = new RecurringScheduleRequest();
        request.setStartDate(from);
        request.setEndDate(to);
        request.setSessions(List.of(sessions));
        return request;
    }

    private RecurringScheduleSessionRequest session(int day, int firstPeriod, int lastPeriod,
                                                    String startTime, String endTime, String room) {
        RecurringScheduleSessionRequest session = new RecurringScheduleSessionRequest();
        session.setDayOfWeek(day);
        session.setStartPeriod(firstPeriod);
        session.setEndPeriod(lastPeriod);
        session.setStartTime(LocalTime.parse(startTime));
        session.setEndTime(LocalTime.parse(endTime));
        session.setClassroom(room);
        return session;
    }

    private void setOtherRecurringRule(int day, int firstPeriod, int lastPeriod,
                                       String startTime, String endTime, String room,
                                       LocalDate from, LocalDate to) {
        RecurringCourseSchedule rule = RecurringCourseSchedule.builder()
                .id(100L)
                .courseSection(other)
                .dayOfWeek(day)
                .startPeriod(firstPeriod)
                .endPeriod(lastPeriod)
                .startTime(LocalTime.parse(startTime))
                .endTime(LocalTime.parse(endTime))
                .classroom(room)
                .startDate(from)
                .endDate(to)
                .build();
        when(recurringScheduleRepository.findByCourseSectionIdInOrderByStartDateAsc(anyCollection()))
                .thenReturn(List.of(rule));
    }
}
