package com.example.student_management.service.exam;

import com.example.student_management.dto.exam.ExamScheduleDto;
import com.example.student_management.dto.exam.ExamScheduleRequest;
import com.example.student_management.dto.exam.ExamStudentDto;
import com.example.student_management.entity.academic.*;
import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.entity.exam.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.repository.academic.CourseSectionRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.repository.exam.ExamRoomRepository;
import com.example.student_management.repository.exam.ExamScheduleRepository;
import com.example.student_management.service.exam.impl.ExamScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleServiceTest {

    @Mock
    private ExamScheduleRepository examScheduleRepository;

    @Mock
    private ExamRoomRepository examRoomRepository;

    @Mock
    private CourseSectionRepository courseSectionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExamScheduleServiceImpl examScheduleService;

    private Course course1;
    private Course course2;
    private CourseSection section1;
    private CourseSection section2;
    private Semester semester;
    private ExamRoom activeRoom;
    private ExamRoom inactiveRoom;
    private Student student1;
    private User studentUser;

    @BeforeEach
    void setUp() {
        semester = Semester.builder().id(1L).semesterName("HK1-2026-2027").build();

        course1 = Course.builder()
                .id(1L)
                .courseCode("INT2204")
                .courseName("Lập trình hướng đối tượng")
                .credits(3)
                .build();

        course2 = Course.builder()
                .id(2L)
                .courseCode("INT3306")
                .courseName("Cơ sở dữ liệu")
                .credits(3)
                .build();

        section1 = CourseSection.builder()
                .id(10L)
                .sectionCode("INT2204-03")
                .course(course1)
                .semester(semester)
                .capacity(40)
                .build();

        section2 = CourseSection.builder()
                .id(20L)
                .sectionCode("INT3306-03")
                .course(course2)
                .semester(semester)
                .capacity(40)
                .build();

        activeRoom = ExamRoom.builder()
                .id(100L)
                .roomCode("P101-A2")
                .roomName("Phòng máy 101")
                .building("Nhà A2")
                .capacity(40)
                .status(ExamRoomStatus.ACTIVE)
                .build();

        inactiveRoom = ExamRoom.builder()
                .id(101L)
                .roomCode("P105-D")
                .roomName("Phòng hỏng điều hòa")
                .building("Nhà D")
                .capacity(30)
                .status(ExamRoomStatus.INACTIVE)
                .build();

        student1 = Student.builder()
                .id(1L)
                .studentCode("SV001")
                .fullName("Nguyễn Văn Sinh")
                .email("sv001@sis.edu.vn")
                .status(AccountStatus.ACTIVE)
                .build();

        studentUser = User.builder()
                .id(1L)
                .username("student")
                .role(Role.STUDENT)
                .student(student1)
                .build();
    }

    @Test
    void testCreateSchedule_Offline_Success() {
        LocalDate examDate = LocalDate.now().plusDays(10);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(10, 0);

        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(examDate)
                .startTime(start)
                .endTime(end)
                .examType(ExamType.OFFLINE)
                .roomId(100L)
                .notes("Thi thực hành")
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(25L);
        when(examRoomRepository.findById(100L)).thenReturn(Optional.of(activeRoom));
        when(examScheduleRepository.findByRoomIdAndExamDateAndStatusNot(100L, examDate, ExamScheduleStatus.CANCELLED))
                .thenReturn(Collections.emptyList());
        when(enrollmentRepository.findByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(Collections.emptyList());
        when(examScheduleRepository.save(any(ExamSchedule.class))).thenAnswer(inv -> {
            ExamSchedule s = inv.getArgument(0);
            s.setId(500L);
            return s;
        });

        ExamScheduleDto dto = examScheduleService.createSchedule(request);

        assertNotNull(dto);
        assertEquals(500L, dto.getId());
        assertEquals("INT2204-03", dto.getSectionCode());
        assertEquals("P101-A2", dto.getRoomCode());
        assertEquals(ExamScheduleStatus.SCHEDULED, dto.getStatus());
        assertEquals(ExamType.OFFLINE, dto.getExamType());
        assertEquals(25, dto.getEnrolledStudentsCount());
    }

    @Test
    void testCreateSchedule_OnlineWithoutRoom_Success() {
        LocalDate examDate = LocalDate.now().plusDays(10);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(examDate)
                .startTime(start)
                .endTime(end)
                .examType(ExamType.ONLINE)
                .examLocation("Google Meet")
                .examLink("https://meet.google.com/test-link")
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(10L);
        when(enrollmentRepository.findByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(Collections.emptyList());
        when(examScheduleRepository.save(any(ExamSchedule.class))).thenAnswer(inv -> {
            ExamSchedule s = inv.getArgument(0);
            s.setId(501L);
            return s;
        });

        ExamScheduleDto dto = examScheduleService.createSchedule(request);

        assertNotNull(dto);
        assertEquals(ExamType.ONLINE, dto.getExamType());
        assertNull(dto.getRoomId());
        assertEquals("Google Meet", dto.getExamLocation());
        assertEquals("https://meet.google.com/test-link", dto.getExamLink());
    }

    @Test
    void testCreateSchedule_EndTimeBeforeStartTime_ThrowsBadRequest() {
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(9, 0))
                .examType(ExamType.ONLINE)
                .build();

        assertThrows(BadRequestException.class, () -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_OfflineWithoutRoom_ThrowsBadRequest() {
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .examType(ExamType.OFFLINE)
                .roomId(null)
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));

        assertThrows(BadRequestException.class, () -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_InactiveRoom_ThrowsBadRequest() {
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .examType(ExamType.OFFLINE)
                .roomId(101L)
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(examRoomRepository.findById(101L)).thenReturn(Optional.of(inactiveRoom));

        assertThrows(BadRequestException.class, () -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_CapacityExceeded_ThrowsConflict() {
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .examType(ExamType.OFFLINE)
                .roomId(100L) // Capacity: 40
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(50L); // 50 > 40
        when(examRoomRepository.findById(100L)).thenReturn(Optional.of(activeRoom));

        assertThrows(ConflictException.class, () -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_RoomConflictOverlappingTime_ThrowsConflict() {
        LocalDate examDate = LocalDate.now().plusDays(5);
        // Ca thi mới: 07:30 - 09:30
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(examDate)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(9, 30))
                .examType(ExamType.OFFLINE)
                .roomId(100L)
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(20L);
        when(examRoomRepository.findById(100L)).thenReturn(Optional.of(activeRoom));

        // Đã có ca thi: 08:30 - 10:30 (Trùng khoảng 08:30 - 09:30)
        ExamSchedule existingSchedule = ExamSchedule.builder()
                .id(999L)
                .courseSection(section2)
                .examDate(examDate)
                .startTime(LocalTime.of(8, 30))
                .endTime(LocalTime.of(10, 30))
                .room(activeRoom)
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findByRoomIdAndExamDateAndStatusNot(100L, examDate, ExamScheduleStatus.CANCELLED))
                .thenReturn(List.of(existingSchedule));

        assertThrows(ConflictException.class, () -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_AdjacentTimeSlot_NoConflict() {
        LocalDate examDate = LocalDate.now().plusDays(5);
        // Ca thi mới: 07:30 - 09:00
        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(examDate)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(9, 0))
                .examType(ExamType.OFFLINE)
                .roomId(100L)
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));
        when(enrollmentRepository.countByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(20L);
        when(examRoomRepository.findById(100L)).thenReturn(Optional.of(activeRoom));

        // Ca thi trước đó: 09:00 - 10:30 (Liền kề, không giao nhau)
        ExamSchedule adjacentSchedule = ExamSchedule.builder()
                .id(999L)
                .courseSection(section2)
                .examDate(examDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .room(activeRoom)
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findByRoomIdAndExamDateAndStatusNot(100L, examDate, ExamScheduleStatus.CANCELLED))
                .thenReturn(List.of(adjacentSchedule));
        when(enrollmentRepository.findByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(Collections.emptyList());
        when(examScheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> examScheduleService.createSchedule(request));
    }

    @Test
    void testCreateSchedule_StudentConflict_ThrowsConflictWithDetails() {
        LocalDate examDate = LocalDate.now().plusDays(5);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(10, 0);

        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(examDate)
                .startTime(start)
                .endTime(end)
                .examType(ExamType.ONLINE)
                .build();

        when(courseSectionRepository.findById(10L)).thenReturn(Optional.of(section1));

        // Sinh viên SV001 tham gia lớp section 1
        Enrollment enrollment1 = Enrollment.builder().id(1L).student(student1).courseSection(section1).status(EnrollmentStatus.ENROLLED).build();
        when(enrollmentRepository.findByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(List.of(enrollment1));

        // Đã có 1 lịch thi khác trên cùng ngày vào lúc 09:00 - 11:00 cho section 2
        ExamSchedule otherSchedule = ExamSchedule.builder()
                .id(888L)
                .courseSection(section2)
                .examDate(examDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findByExamDateAndStatusNot(examDate, ExamScheduleStatus.CANCELLED))
                .thenReturn(List.of(otherSchedule));

        // Sinh viên SV001 cũng tham gia lớp section 2!
        Enrollment enrollment2 = Enrollment.builder().id(2L).student(student1).courseSection(section2).status(EnrollmentStatus.ENROLLED).build();
        when(enrollmentRepository.findByCourseSectionIdInAndStatus(
                eq(java.util.Set.of(20L)), eq(EnrollmentStatus.ENROLLED)))
                .thenReturn(List.of(enrollment2));

        ConflictException ex = assertThrows(ConflictException.class, () -> examScheduleService.createSchedule(request));
        assertTrue(ex.getMessage().contains("SV001"));
        assertTrue(ex.getMessage().contains("Nguyễn Văn Sinh"));
    }

    @Test
    void testUpdateSchedule_CompletedSchedule_CannotChangeDateOrTime() {
        ExamSchedule completedSchedule = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .examDate(LocalDate.now().minusDays(5))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(ExamScheduleStatus.COMPLETED)
                .build();

        when(examScheduleRepository.findById(1L)).thenReturn(Optional.of(completedSchedule));

        ExamScheduleRequest request = ExamScheduleRequest.builder()
                .courseSectionId(10L)
                .examDate(LocalDate.now()) // Cố tình đổi ngày
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .examType(ExamType.ONLINE)
                .status(ExamScheduleStatus.COMPLETED)
                .build();

        assertThrows(BadRequestException.class, () -> examScheduleService.updateSchedule(1L, request));
    }

    @Test
    void testCancelSchedule_Success() {
        ExamSchedule scheduled = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findById(1L)).thenReturn(Optional.of(scheduled));
        when(examScheduleRepository.save(any(ExamSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamScheduleDto result = examScheduleService.cancelSchedule(1L);

        assertNotNull(result);
        assertEquals(ExamScheduleStatus.CANCELLED, result.getStatus());
    }

    @Test
    void testCancelSchedule_Completed_ThrowsBadRequest() {
        ExamSchedule completed = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .status(ExamScheduleStatus.COMPLETED)
                .build();

        when(examScheduleRepository.findById(1L)).thenReturn(Optional.of(completed));

        assertThrows(BadRequestException.class, () -> examScheduleService.cancelSchedule(1L));
    }

    @Test
    void testGetStudentSchedules_OnlyOwnEnrolledSections() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        Enrollment enr = Enrollment.builder().student(student1).courseSection(section1).status(EnrollmentStatus.ENROLLED).build();
        when(enrollmentRepository.findByStudentIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(List.of(enr));

        ExamSchedule schedule1 = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .examDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findByCourseSectionIdIn(List.of(10L))).thenReturn(List.of(schedule1));

        List<ExamScheduleDto> result = examScheduleService.getStudentSchedules("student", null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("INT2204-03", result.get(0).getSectionCode());
    }

    @Test
    void testGetStudentScheduleDetail_NotEnrolled_ThrowsForbidden() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        ExamSchedule schedule2 = ExamSchedule.builder()
                .id(2L)
                .courseSection(section2) // Section 2 mà sinh viên không học
                .build();

        when(examScheduleRepository.findById(2L)).thenReturn(Optional.of(schedule2));
        when(enrollmentRepository.existsByStudentIdAndCourseSectionIdAndStatus(1L, 20L, EnrollmentStatus.ENROLLED))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () -> examScheduleService.getStudentScheduleDetail("student", 2L));
    }

    @Test
    void testGetStudentScheduleDetail_Enrolled_Success() {
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(studentUser));

        ExamSchedule schedule1 = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .examDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .status(ExamScheduleStatus.SCHEDULED)
                .build();

        when(examScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule1));
        when(enrollmentRepository.existsByStudentIdAndCourseSectionIdAndStatus(1L, 10L, EnrollmentStatus.ENROLLED))
                .thenReturn(true);

        ExamScheduleDto result = examScheduleService.getStudentScheduleDetail("student", 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("INT2204-03", result.getSectionCode());
    }

    @Test
    void testGetStudentsByScheduleId_Success() {
        ExamSchedule schedule1 = ExamSchedule.builder()
                .id(1L)
                .courseSection(section1)
                .build();

        when(examScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule1));

        Enrollment enr = Enrollment.builder()
                .id(10L)
                .student(student1)
                .courseSection(section1)
                .status(EnrollmentStatus.ENROLLED)
                .build();

        when(enrollmentRepository.findByCourseSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(List.of(enr));

        List<ExamStudentDto> students = examScheduleService.getStudentsByScheduleId(1L);

        assertNotNull(students);
        assertEquals(1, students.size());
        assertEquals("SV001", students.get(0).getStudentCode());
        assertEquals("Nguyễn Văn Sinh", students.get(0).getFullName());
    }
}
