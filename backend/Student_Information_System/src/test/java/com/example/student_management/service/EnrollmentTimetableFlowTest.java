package com.example.student_management.service;

import com.example.student_management.dto.timetable.TimetableWeekResponse;
import com.example.student_management.entity.academic.Course;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.CourseSectionSchedule;
import com.example.student_management.entity.academic.CourseSectionStatus;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.EnrollmentStatus;
import com.example.student_management.entity.academic.Semester;
import com.example.student_management.entity.academic.SemesterStatus;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Lecturer;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.repository.academic.SemesterRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.academic.impl.EnrollmentServiceImpl;
import com.example.student_management.service.notification.NotificationService;
import com.example.student_management.service.timetable.impl.TimetableServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentTimetableFlowTest {
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
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;
    @InjectMocks
    private TimetableServiceImpl timetableService;

    private Enrollment enrollment;
    private User admin;

    @BeforeEach
    void setUp() {
        Semester semester = Semester.builder()
                .id(3L)
                .semesterCode("HK1")
                .semesterName("Học kỳ 1")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 1, 15))
                .status(SemesterStatus.REGISTRATION_OPEN)
                .build();
        Student student = Student.builder()
                .id(10L)
                .studentCode("SV001")
                .fullName("Nguyễn An")
                .status(AccountStatus.ACTIVE)
                .build();
        User studentUser = User.builder().username("student1").student(student).build();
        student.setUser(studentUser);
        admin = User.builder().id(1L).username("admin").build();
        Lecturer lecturer = Lecturer.builder()
                .id(40L)
                .lecturerCode("GV001")
                .fullName("Giảng viên A")
                .email("lecturer@example.edu")
                .status(AccountStatus.ACTIVE)
                .build();
        CourseSection section = CourseSection.builder()
                .id(20L)
                .sectionCode("CS101-01")
                .course(Course.builder()
                        .id(30L)
                        .courseCode("CS101")
                        .courseName("Lập trình")
                        .credits(3)
                        .build())
                .semester(semester)
                .lecturer(lecturer)
                .schedule("Thứ 2, 09:00 - 10:00, Phòng A1")
                .capacity(40)
                .status(CourseSectionStatus.OPEN)
                .build();
        enrollment = Enrollment.builder()
                .id(4L)
                .student(student)
                .courseSection(section)
                .status(EnrollmentStatus.PENDING)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(studentUser));
        when(enrollmentRepository.findById(4L)).thenAnswer(invocation -> Optional.of(enrollment));
        when(enrollmentRepository.findByStudentId(10L)).thenAnswer(invocation ->
                enrollment.getStatus() == EnrollmentStatus.WITHDRAWN ? List.of() : List.of(enrollment));
        when(enrollmentRepository.findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L))
                .thenAnswer(invocation ->
                        enrollment.getStatus() == EnrollmentStatus.ENROLLED
                                ? List.of(enrollment, enrollment)
                                : List.of());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            enrollment = invocation.getArgument(0);
            if (enrollment.getId() == null) {
                enrollment.setId(4L);
            }
            return enrollment;
        });
        when(enrollmentRepository.countByCourseSectionIdAndStatusInAndIdNot(
                eq(20L), anyCollection(), eq(4L))).thenReturn(0L);
        when(enrollmentRepository.approveIfPending(eq(4L), eq(EnrollmentStatus.PENDING),
                eq(EnrollmentStatus.ENROLLED), any(LocalDateTime.class), eq(admin)))
                .thenReturn(1);
        when(courseSectionRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(section));
        when(courseSectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(20L))
                .thenReturn(List.of());
        when(courseSectionScheduleRepository.findByCourseSectionIdInOrderByEffectiveFromAsc(anyCollection()))
                .thenReturn(List.<CourseSectionSchedule>of());
        ReflectionTestUtils.setField(enrollmentService, "maxCreditsPerSemester", 24);
    }

    @Test
    void approvalMakesEnrollmentVisibleInTimetableUntilStudentWithdraws() {
        Enrollment approved = enrollmentService.approve("admin", 4L);

        assertEquals(EnrollmentStatus.ENROLLED, approved.getStatus());
        assertNotNull(approved.getApprovedAt());
        assertEquals("admin", approved.getApprovedBy().getUsername());
        verify(enrollmentRepository).approveIfPending(
                eq(4L), eq(EnrollmentStatus.PENDING), eq(EnrollmentStatus.ENROLLED),
                any(LocalDateTime.class), any(User.class));
        when(enrollmentRepository.findByStudentIdOrderByRegisteredAtDesc(10L))
                .thenReturn(List.of(approved));
        assertEquals(EnrollmentStatus.ENROLLED,
                enrollmentService.getMyEnrollments("student1").get(0).getStatus());

        TimetableWeekResponse afterApproval = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10);
        assertEquals(1, afterApproval.getTotalElements());
        assertEquals(1, afterApproval.getItems().size());
        assertEquals("Lập trình", afterApproval.getItems().get(0).getCourseName());
        assertEquals("CS101-01", afterApproval.getItems().get(0).getSectionCode());
        assertEquals(LocalDate.of(2026, 11, 9), afterApproval.getItems().get(0).getDate());
        assertEquals("09:00", afterApproval.getItems().get(0).getStartTime());
        assertEquals("10:00", afterApproval.getItems().get(0).getEndTime());
        assertEquals("Phòng A1", afterApproval.getItems().get(0).getRoom());
        assertEquals("Giảng viên A", afterApproval.getItems().get(0).getLecturerName());
        verify(enrollmentRepository).findForTimetableBySemester(10L, EnrollmentStatus.ENROLLED, 3L);

        enrollmentService.withdraw("student1", 4L);
        TimetableWeekResponse afterWithdrawal = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10);

        assertEquals(0, afterWithdrawal.getTotalElements());
        assertEquals(List.of(), afterWithdrawal.getItems());
    }

    @Test
    void approvalRejectsStudentsOnLeave() {
        enrollment.getStudent().setAcademicStatus("Đang bảo lưu");

        assertThrows(ForbiddenException.class, () -> enrollmentService.approve("admin", 4L));
    }

    @Test
    void studentCanRegisterBeforeApprovalAndSeeTheApprovedSectionAfterward() {
        when(enrollmentRepository.findByStudentIdAndCourseSectionId(10L, 20L))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.countByCourseSectionIdAndStatusIn(any(), anyCollection()))
                .thenReturn(0L);

        Enrollment pending = enrollmentService.enroll("student1", 20L);
        assertEquals(EnrollmentStatus.PENDING, pending.getStatus());

        Enrollment approved = enrollmentService.approve("admin", pending.getId());
        assertEquals(EnrollmentStatus.ENROLLED, approved.getStatus());

        TimetableWeekResponse timetable = timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10);
        assertEquals(1, timetable.getTotalElements());
        assertEquals("CS101-01", timetable.getItems().get(0).getSectionCode());
    }

    @Test
    void rejectedPendingEnrollmentIsNotInTimetableAndRecordsRejectingAdmin() {
        when(enrollmentRepository.rejectIfPending(eq(4L), eq(EnrollmentStatus.PENDING),
                eq(EnrollmentStatus.REJECTED), any(LocalDateTime.class), eq("Not eligible"), eq(admin)))
                .thenReturn(1);

        Enrollment rejected = enrollmentService.reject("admin", 4L, "Not eligible");

        assertEquals(EnrollmentStatus.REJECTED, rejected.getStatus());
        assertEquals("Not eligible", rejected.getRejectionReason());
        assertEquals("admin", rejected.getRejectedBy().getUsername());
        assertNotNull(rejected.getRejectedAt());
        assertEquals(0, timetableService.getWeek(
                "student1", 3L, LocalDate.of(2026, 11, 12), 1, 10).getTotalElements());
    }

    @Test
    void approvalFailsIfAnotherAdminAlreadyProcessedTheRequest() {
        when(enrollmentRepository.approveIfPending(eq(4L), eq(EnrollmentStatus.PENDING),
                eq(EnrollmentStatus.ENROLLED), any(LocalDateTime.class), eq(admin)))
                .thenReturn(0);

        assertThrows(BadRequestException.class, () -> enrollmentService.approve("admin", 4L));
    }

    @Test
    void registrationCannotExceedThePerSemesterCreditLimit() {
        CourseSection existingSection = CourseSection.builder()
                .id(25L)
                .sectionCode("CS200-01")
                .course(Course.builder().credits(22).build())
                .semester(enrollment.getCourseSection().getSemester())
                .schedule("Thứ 3, 09:00 - 10:00, B1")
                .build();
        Enrollment existing = Enrollment.builder()
                .id(7L)
                .student(enrollment.getStudent())
                .courseSection(existingSection)
                .status(EnrollmentStatus.ENROLLED)
                .build();
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(List.of(existing));
        when(enrollmentRepository.countByCourseSectionIdAndStatusIn(20L, List.of(
                EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED))).thenReturn(0L);

        assertThrows(ConflictException.class, () -> enrollmentService.enroll("student1", 20L));
    }

    @Test
    void registrationCannotExceedSectionCapacity() {
        enrollment.getCourseSection().setCapacity(1);
        when(enrollmentRepository.countByCourseSectionIdAndStatusIn(20L, List.of(
                EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED))).thenReturn(1L);

        assertThrows(ConflictException.class, () -> enrollmentService.enroll("student1", 20L));
    }
}
