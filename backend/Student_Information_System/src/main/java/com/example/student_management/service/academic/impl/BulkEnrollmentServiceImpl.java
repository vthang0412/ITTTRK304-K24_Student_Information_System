package com.example.student_management.service.academic.impl;

import com.example.student_management.dto.academic.*;
import com.example.student_management.entity.academic.*;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.ForbiddenException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.academic.EnrollmentBatchOperationItemRepository;
import com.example.student_management.repository.academic.EnrollmentBatchOperationRepository;
import com.example.student_management.repository.academic.EnrollmentRepository;
import com.example.student_management.repository.academic.CourseSectionScheduleRepository;
import com.example.student_management.repository.academic.RecurringCourseScheduleRepository;
import com.example.student_management.service.academic.BulkEnrollmentService;
import com.example.student_management.service.academic.EnrollmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BulkEnrollmentServiceImpl implements BulkEnrollmentService {
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentBatchOperationRepository operationRepository;
    private final EnrollmentBatchOperationItemRepository operationItemRepository;
    private final RecurringCourseScheduleRepository recurringScheduleRepository;
    private final CourseSectionScheduleRepository sectionScheduleRepository;
    private final PlatformTransactionManager transactionManager;

    public BulkEnrollmentServiceImpl(
            EnrollmentService enrollmentService,
            EnrollmentRepository enrollmentRepository,
            EnrollmentBatchOperationRepository operationRepository,
            EnrollmentBatchOperationItemRepository operationItemRepository,
            RecurringCourseScheduleRepository recurringScheduleRepository,
            CourseSectionScheduleRepository sectionScheduleRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.enrollmentService = enrollmentService;
        this.enrollmentRepository = enrollmentRepository;
        this.operationRepository = operationRepository;
        this.operationItemRepository = operationItemRepository;
        this.recurringScheduleRepository = recurringScheduleRepository;
        this.sectionScheduleRepository = sectionScheduleRepository;
        this.transactionManager = transactionManager;
    }

    @Override
    public BulkEnrollmentResponse approve(String adminUsername, List<Long> enrollmentIds) {
        return process(adminUsername, enrollmentIds, null, null, true);
    }

    @Override
    public BulkEnrollmentResponse reject(String adminUsername, List<Long> enrollmentIds, String reason) {
        return reject(adminUsername, enrollmentIds, reason, null);
    }

    @Override
    public BulkEnrollmentResponse reject(
            String adminUsername,
            List<Long> enrollmentIds,
            String reason,
            Map<Long, String> reasonsByEnrollmentId
    ) {
        Set<Long> selectedIds = new LinkedHashSet<>(enrollmentIds);
        if (reasonsByEnrollmentId != null && !selectedIds.containsAll(reasonsByEnrollmentId.keySet())) {
            throw new BadRequestException("Lý do từ chối chỉ được gửi cho các yêu cầu đã chọn");
        }
        return process(adminUsername, enrollmentIds, reason, reasonsByEnrollmentId, false);
    }

    @Override
    public BulkEnrollmentPreviewResponse preview(String adminUsername, List<Long> enrollmentIds, String action) {
        List<BulkEnrollmentPreviewItem> results = new ArrayList<>();
        Set<Long> processedIds = new HashSet<>();
        int eligibleCount = 0;
        int skippedCount = 0;

        for (Long enrollmentId : enrollmentIds) {
            if (!processedIds.add(enrollmentId)) {
                Enrollment enrollment = loadEnrollment(enrollmentId);
                results.add(previewItem(enrollmentId, enrollment, false, "SKIPPED",
                        "ID bị lặp trong danh sách và sẽ không được xử lý.", null));
                skippedCount++;
                continue;
            }

            Enrollment enrollment = loadEnrollment(enrollmentId);
            if (enrollment == null) {
                results.add(previewItem(enrollmentId, null, false, "SKIPPED",
                        "Không tìm thấy yêu cầu đăng ký; yêu cầu này sẽ bị bỏ qua.", null));
                skippedCount++;
                continue;
            }

            if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
                results.add(previewItem(enrollmentId, enrollment, false, "SKIPPED",
                        "Yêu cầu không còn ở trạng thái chờ duyệt.", null));
                skippedCount++;
                continue;
            }

            try {
                Boolean scheduleConfigured = null;
                if ("APPROVE".equals(action)) {
                    enrollmentService.validateApproval(enrollmentId);
                    scheduleConfigured = hasSchedule(enrollment);
                } else if (!"REJECT".equals(action)) {
                    throw new BadRequestException("Hành động không hợp lệ.");
                }
                results.add(previewItem(enrollmentId, enrollment, true, "ELIGIBLE",
                        "Đủ điều kiện tại thời điểm kiểm tra.", scheduleConfigured));
                eligibleCount++;
            } catch (BadRequestException | ConflictException | ForbiddenException | NotFoundException exception) {
                results.add(previewItem(enrollmentId, enrollment, false, "FAILED",
                        message(exception), null));
            }
        }

        return new BulkEnrollmentPreviewResponse(
                enrollmentIds.size(),
                eligibleCount,
                enrollmentIds.size() - eligibleCount - skippedCount,
                skippedCount,
                results
        );
    }

    @Override
    public Page<BulkEnrollmentHistoryDto> findHistory(
            String admin,
            Long semesterId,
            String action,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            String student,
            String course,
            String section,
            String result,
            Pageable pageable
    ) {
        return operationRepository.search(
                        blankToNull(admin), semesterId, blankToNull(action), fromTime, toTime,
                        blankToNull(student), blankToNull(course), blankToNull(section), blankToNull(result), pageable)
                .map(this::toHistoryDto);
    }

    @Override
    public BulkEnrollmentHistoryDetailDto getHistory(Long operationId) {
        EnrollmentBatchOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch sử xử lý hàng loạt."));
        List<BulkEnrollmentResult> results = operationItemRepository
                .findByBatchIdOrderByIdAsc(operationId)
                .stream()
                .map(this::toResult)
                .toList();
        return new BulkEnrollmentHistoryDetailDto(toHistoryDto(operation), results);
    }

    private BulkEnrollmentResponse process(
            String adminUsername,
            List<Long> enrollmentIds,
            String reason,
            Map<Long, String> reasonsByEnrollmentId,
            boolean approve
    ) {
        EnrollmentBatchOperation operation = createOperation(adminUsername, enrollmentIds, reason,
                reasonsByEnrollmentId, approve);
        String action = approve ? "APPROVE" : "REJECT";
        List<BulkEnrollmentResult> results = new ArrayList<>(enrollmentIds.size());
        Set<Long> uniqueIds = new LinkedHashSet<>();

        for (Long enrollmentId : enrollmentIds) {
            if (!uniqueIds.add(enrollmentId)) {
                Enrollment enrollment = loadEnrollment(enrollmentId);
                BulkEnrollmentResult duplicate = toResult(enrollment, enrollmentId, action, "SKIPPED",
                        "ID bị lặp trong danh sách và đã được bỏ qua.", null, null);
                results.add(duplicate);
                saveItem(operation.getId(), duplicate);
                continue;
            }

            String itemReason = reasonsByEnrollmentId != null
                    ? reasonsByEnrollmentId.getOrDefault(enrollmentId, reason)
                    : reason;
            try {
                results.add(processOne(operation.getId(), adminUsername, enrollmentId, itemReason, approve));
            } catch (BadRequestException | ConflictException | ForbiddenException | NotFoundException exception) {
                Enrollment enrollment = loadEnrollment(enrollmentId);
                boolean skipped = enrollment == null || enrollment.getStatus() != EnrollmentStatus.PENDING;
                String resultStatus = skipped ? "SKIPPED" : "FAILED";
                BulkEnrollmentResult failure = toResult(enrollment, enrollmentId, action, resultStatus,
                        message(exception), null, itemReason);
                results.add(failure);
                saveItem(operation.getId(), failure);
            }
        }

        int successCount = (int) results.stream().filter(result -> "SUCCESS".equals(result.resultStatus())).count();
        int skippedCount = (int) results.stream().filter(result -> "SKIPPED".equals(result.resultStatus())).count();
        int failureCount = results.size() - successCount - skippedCount;
        finalizeOperation(operation.getId(), successCount, failureCount, skippedCount);
        return new BulkEnrollmentResponse(enrollmentIds.size(), successCount, failureCount, skippedCount, results);
    }

    private EnrollmentBatchOperation createOperation(
            String adminUsername,
            List<Long> ids,
            String reason,
            Map<Long, String> reasonsById,
            boolean approve
    ) {
        List<Enrollment> enrollments = ids.isEmpty()
                ? List.of()
                : enrollmentRepository.findAdminDetailsByIdIn(new LinkedHashSet<>(ids));
        Set<Long> semesters = new LinkedHashSet<>();
        for (Enrollment enrollment : enrollments) {
            if (enrollment.getCourseSection() != null && enrollment.getCourseSection().getSemester() != null) {
                semesters.add(enrollment.getCourseSection().getSemester().getId());
            }
        }

        Long semesterId = semesters.size() == 1 ? semesters.iterator().next() : null;
        String semesterName = semesters.size() > 1 ? "Nhiều học kỳ"
                : enrollments.stream()
                        .filter(enrollment -> enrollment.getCourseSection() != null)
                        .map(Enrollment::getCourseSection)
                        .filter(section -> section.getSemester() != null)
                        .map(section -> section.getSemester().getSemesterName())
                        .findFirst()
                        .orElse(null);
        String finalReason = approve ? null : commonReason(reason, reasonsById);

        EnrollmentBatchOperation operation = EnrollmentBatchOperation.builder()
                .batchCode(UUID.randomUUID().toString())
                .adminUsername(adminUsername)
                .action(approve ? "APPROVE" : "REJECT")
                .semesterId(semesterId)
                .semesterName(semesterName)
                .selectedCount(ids.size())
                .successCount(0)
                .failureCount(0)
                .skippedCount(0)
                .rejectionReason(finalReason)
                .build();
        return operationRepository.save(operation);
    }

    private BulkEnrollmentResult processOne(
            Long batchId,
            String adminUsername,
            Long enrollmentId,
            String reason,
            boolean approve
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
            Enrollment enrollment = enrollmentRepository.findAdminDetailsById(enrollmentId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu đăng ký."));

            if (approve) {
                enrollmentService.approve(adminUsername, enrollmentId);
            } else {
                enrollmentService.reject(adminUsername, enrollmentId, reason);
            }

            Boolean scheduleConfigured = approve ? hasSchedule(enrollment) : null;
            BulkEnrollmentResult result = toResult(enrollment, enrollmentId,
                    approve ? "APPROVE" : "REJECT", "SUCCESS",
                    approve ? "Đã duyệt." : "Đã từ chối.", scheduleConfigured, reason);
            operationItemRepository.save(toItem(batchId, result));
            return result;
        });
    }

    private void saveItem(Long batchId, BulkEnrollmentResult result) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> operationItemRepository.save(toItem(batchId, result)));
    }

    private void finalizeOperation(Long operationId, int successCount, int failureCount, int skippedCount) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            EnrollmentBatchOperation operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy đợt xử lý hàng loạt."));
            operation.setSuccessCount(successCount);
            operation.setFailureCount(failureCount);
            operation.setSkippedCount(skippedCount);
            operationRepository.save(operation);
        });
    }

    private Enrollment loadEnrollment(Long enrollmentId) {
        if (enrollmentId == null) {
            return null;
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        return transaction.execute(status -> enrollmentRepository.findAdminDetailsById(enrollmentId).orElse(null));
    }

    private boolean hasSchedule(Enrollment enrollment) {
        if (enrollment.getCourseSection() == null) {
            return false;
        }
        CourseSection section = enrollment.getCourseSection();
        return (section.getSchedule() != null && !section.getSchedule().isBlank())
                || !recurringScheduleRepository.findByCourseSectionIdOrderByDayOfWeekAscStartTimeAsc(section.getId()).isEmpty()
                || !sectionScheduleRepository.findByCourseSectionIdOrderByEffectiveFromAsc(section.getId()).isEmpty();
    }

    private BulkEnrollmentPreviewItem previewItem(
            Long enrollmentId,
            Enrollment enrollment,
            boolean eligible,
            String resultStatus,
            String message,
            Boolean scheduleConfigured
    ) {
        CourseSection section = enrollment != null ? enrollment.getCourseSection() : null;
        var student = enrollment != null ? enrollment.getStudent() : null;
        var course = section != null ? section.getCourse() : null;
        var semester = section != null ? section.getSemester() : null;
        return new BulkEnrollmentPreviewItem(
                enrollmentId,
                student != null ? student.getStudentCode() : null,
                student != null ? student.getFullName() : null,
                student != null ? student.getEmail() : null,
                course != null ? course.getCourseCode() : null,
                course != null ? course.getCourseName() : null,
                section != null ? section.getSectionCode() : null,
                semester != null ? semester.getSemesterName() : null,
                enrollment != null && enrollment.getStatus() != null ? enrollment.getStatus().name() : null,
                eligible,
                resultStatus,
                message,
                scheduleConfigured
        );
    }

    private BulkEnrollmentResult toResult(
            Enrollment enrollment,
            Long enrollmentId,
            String action,
            String resultStatus,
            String message,
            Boolean scheduleConfigured,
            String rejectionReason
    ) {
        CourseSection section = enrollment != null ? enrollment.getCourseSection() : null;
        var student = enrollment != null ? enrollment.getStudent() : null;
        var course = section != null ? section.getCourse() : null;
        var semester = section != null ? section.getSemester() : null;
        return new BulkEnrollmentResult(
                enrollmentId,
                student != null ? student.getFullName() : null,
                student != null ? student.getStudentCode() : null,
                section != null ? section.getSectionCode() : null,
                "SUCCESS".equals(resultStatus),
                message,
                scheduleConfigured,
                course != null ? course.getCourseCode() : null,
                course != null ? course.getCourseName() : null,
                semester != null ? semester.getSemesterName() : null,
                action,
                resultStatus,
                rejectionReason,
                student != null ? student.getEmail() : null,
                enrollment != null ? enrollment.getRegisteredAt() : null
        );
    }

    private EnrollmentBatchOperationItem toItem(Long batchId, BulkEnrollmentResult result) {
        return EnrollmentBatchOperationItem.builder()
                .batchId(batchId)
                .enrollmentId(result.enrollmentId())
                .studentCode(result.studentCode())
                .studentName(result.studentName())
                .studentEmail(result.studentEmail())
                .courseCode(result.courseCode())
                .courseName(result.courseName())
                .sectionCode(result.sectionCode())
                .semesterName(result.semesterName())
                .registeredAt(result.registeredAt())
                .action(result.action())
                .resultStatus(result.resultStatus())
                .message(result.message())
                .rejectionReason(result.rejectionReason())
                .scheduleConfigured(result.scheduleConfigured())
                .build();
    }

    private BulkEnrollmentResult toResult(EnrollmentBatchOperationItem item) {
        return new BulkEnrollmentResult(
                item.getEnrollmentId(), item.getStudentName(), item.getStudentCode(), item.getSectionCode(),
                "SUCCESS".equals(item.getResultStatus()), item.getMessage(), item.getScheduleConfigured(),
                item.getCourseCode(), item.getCourseName(), item.getSemesterName(), item.getAction(),
                item.getResultStatus(), item.getRejectionReason(), item.getStudentEmail(), item.getRegisteredAt());
    }

    private BulkEnrollmentHistoryDto toHistoryDto(EnrollmentBatchOperation operation) {
        return new BulkEnrollmentHistoryDto(
                operation.getId(), operation.getBatchCode(), operation.getAdminUsername(), operation.getCreatedAt(),
                operation.getAction(), operation.getSemesterId(), operation.getSemesterName(),
                operation.getSelectedCount(), operation.getSuccessCount(), operation.getFailureCount(),
                operation.getSkippedCount(), operation.getRejectionReason());
    }

    private String commonReason(String reason, Map<Long, String> reasonsById) {
        if (reasonsById == null || reasonsById.isEmpty()) {
            return trimOrNull(reason);
        }
        Set<String> reasons = new LinkedHashSet<>();
        if (reason != null && !reason.isBlank()) {
            reasons.add(reason.trim());
        }
        reasonsById.values().stream().map(this::trimOrNull).filter(Objects::nonNull).forEach(reasons::add);
        return reasons.size() == 1 ? reasons.iterator().next() : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Không thể xử lý yêu cầu."
                : exception.getMessage();
    }
}
