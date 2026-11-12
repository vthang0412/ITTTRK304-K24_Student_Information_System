package com.example.student_management.service;

import com.example.student_management.dto.academic.BulkEnrollmentResponse;
import com.example.student_management.entity.academic.CourseSection;
import com.example.student_management.entity.academic.EnrollmentBatchOperation;
import com.example.student_management.entity.academic.Enrollment;
import com.example.student_management.entity.academic.RecurringCourseSchedule;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.EnrollmentBatchOperationItemRepository;
import com.example.student_management.repository.academic.EnrollmentBatchOperationRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.service.academic.EnrollmentService;
import com.example.student_management.service.academic.impl.BulkEnrollmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkEnrollmentServiceTest {
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private EnrollmentBatchOperationRepository operationRepository;
    @Mock
    private EnrollmentBatchOperationItemRepository operationItemRepository;
    @Mock
    private RecurringCourseScheduleRepository recurringScheduleRepository;
    @Mock
    private CourseSectionScheduleRepository sectionScheduleRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private BulkEnrollmentServiceImpl bulkEnrollmentService;

    @BeforeEach
    void setUp() {
        bulkEnrollmentService = new BulkEnrollmentServiceImpl(
                enrollmentService,
                enrollmentRepository,
                operationRepository,
                operationItemRepository,
                recurringScheduleRepository,
                sectionScheduleRepository,
                transactionManager
        );
        lenient().when(operationRepository.save(any(EnrollmentBatchOperation.class))).thenAnswer(invocation -> {
            EnrollmentBatchOperation operation = invocation.getArgument(0);
            if (operation.getId() == null) operation.setId(100L);
            return operation;
        });
        lenient().when(operationRepository.findById(100L))
                .thenAnswer(invocation -> Optional.of(EnrollmentBatchOperation.builder().id(100L).build()));
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        lenient().when(enrollmentRepository.findById(any())).thenAnswer(invocation -> Optional.of(enrollment(
                (Long) invocation.getArgument(0), "CS101-01")));
        lenient().when(enrollmentRepository.findAdminDetailsById(any())).thenAnswer(invocation -> Optional.of(enrollment(
                (Long) invocation.getArgument(0), "CS101-01")));
        lenient().when(recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(20L))
                .thenReturn(List.of());
        lenient().when(sectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(20L))
                .thenReturn(List.of());
    }

    @Test
    void approvesEachRequestInItsOwnTransactionAndReportsScheduleAbsence() {
        when(enrollmentService.approve("admin", 1L)).thenReturn(enrollment(1L, "CS101-01"));
        doThrow(new ConflictException("Lớp học phần đã đủ số lượng"))
                .when(enrollmentService).approve("admin", 2L);

        BulkEnrollmentResponse result = bulkEnrollmentService.approve("admin", List.of(1L, 2L, 1L));

        assertEquals(3, result.requestedCount());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, result.skippedCount());
        assertTrue(result.results().get(0).success());
        assertFalse(result.results().get(0).scheduleConfigured());
        assertFalse(result.results().get(1).success());
        assertEquals("Lớp học phần đã đủ số lượng", result.results().get(1).message());
        assertEquals("SKIPPED", result.results().get(2).resultStatus());
        verify(operationRepository, org.mockito.Mockito.times(2)).save(any(EnrollmentBatchOperation.class));
        verify(operationItemRepository, org.mockito.Mockito.times(3)).save(any());
        verify(transactionManager, org.mockito.Mockito.atLeast(2))
                .getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void reportsExistingRecurringScheduleWithoutCreatingStudentSpecificSchedule() {
        when(enrollmentService.approve("admin", 1L)).thenReturn(enrollment(1L, "CS101-01"));
        when(recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(20L))
                .thenReturn(List.of(RecurringCourseSchedule.builder().id(10L).build()));

        BulkEnrollmentResponse result = bulkEnrollmentService.approve("admin", List.of(1L));

        assertTrue(result.results().get(0).scheduleConfigured());
        verify(enrollmentService).approve("admin", 1L);
    }

    @Test
    void rejectsRequestsUsingTheExistingRejectionReasonFlow() {
        when(enrollmentService.reject("admin", 1L, "Không đủ điều kiện"))
                .thenReturn(enrollment(1L, "CS101-01"));

        BulkEnrollmentResponse result = bulkEnrollmentService.reject(
                "admin", List.of(1L), "Không đủ điều kiện");

        assertEquals(1, result.successCount());
        assertEquals("Đã từ chối.", result.results().get(0).message());
        assertNull(result.results().get(0).scheduleConfigured());
        verify(enrollmentService).reject("admin", 1L, "Không đủ điều kiện");
    }

    @Test
    void appliesDifferentReasonsOnlyToSelectedEnrollmentIds() {
        when(enrollmentService.reject("admin", 1L, "Sinh viên đang bảo lưu"))
                .thenReturn(enrollment(1L, "CS101-01"));
        when(enrollmentService.reject("admin", 2L, "Đăng ký sai học phần"))
                .thenReturn(enrollment(2L, "CS101-01"));

        BulkEnrollmentResponse result = bulkEnrollmentService.reject(
                "admin",
                List.of(1L, 2L),
                "Sinh viên đang bảo lưu",
                Map.of(2L, "Đăng ký sai học phần"));

        assertEquals(2, result.successCount());
        verify(enrollmentService).reject("admin", 1L, "Sinh viên đang bảo lưu");
        verify(enrollmentService).reject("admin", 2L, "Đăng ký sai học phần");
    }

    @Test
    void doesNotAcceptReasonsForUnselectedEnrollments() {
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class, () ->
                bulkEnrollmentService.reject(
                        "admin", List.of(1L), "Lý do chung", Map.of(2L, "Lý do ngoài lựa chọn")));
    }

    @Test
    void previewsEachSelectedEnrollmentAndReportsEligibilityIndividually() {
        lenient().doThrow(new ConflictException("Lớp học phần đã đủ số lượng"))
                .when(enrollmentService).validateApproval(2L);

        var result = bulkEnrollmentService.preview("admin", List.of(1L, 2L, 1L), "APPROVE");

        assertEquals(3, result.requestedCount());
        assertEquals(1, result.eligibleCount());
        assertEquals(1, result.ineligibleCount());
        assertEquals(1, result.skippedCount());
        assertTrue(result.results().get(0).eligible());
        assertFalse(result.results().get(1).eligible());
        assertEquals("Lớp học phần đã đủ số lượng", result.results().get(1).message());
        assertEquals("SKIPPED", result.results().get(2).resultStatus());
        verify(enrollmentService).validateApproval(1L);
        verify(enrollmentService).validateApproval(2L);
    }

    private Enrollment enrollment(Long id, String sectionCode) {
        return Enrollment.builder()
                .id(id)
                .student(Student.builder().studentCode("SV" + id).fullName("Sinh viên " + id).build())
                .courseSection(CourseSection.builder().id(20L).sectionCode(sectionCode).build())
                .status(com.example.student_management.entity.academic.EnrollmentStatus.PENDING)
                .build();
    }
}
