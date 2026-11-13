package com.example.student_management.service.grade;

import com.example.student_management.dto.grade.*;
import com.example.student_management.entity.grade.GradebookBulkOperation;
import com.example.student_management.entity.grade.GradebookBulkOperationItem;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.grade.GradebookBulkOperationItemRepository;
import com.example.student_management.repository.grade.GradebookBulkOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GradebookBulkActionService {
    private final GradebookService gradebookService;
    private final GradebookBulkOperationRepository operationRepository;
    private final GradebookBulkOperationItemRepository itemRepository;

    public GradebookBulkActionService(
            GradebookService gradebookService,
            GradebookBulkOperationRepository operationRepository,
            GradebookBulkOperationItemRepository itemRepository) {
        this.gradebookService = gradebookService;
        this.operationRepository = operationRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public GradebookBulkActionPreviewResponse preview(GradebookBulkActionRequest request) {
        List<GradebookBulkActionPreviewItem> results = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();

        for (Long gradebookId : request.getGradebookIds()) {
            if (!uniqueIds.add(gradebookId)) {
                results.add(GradebookBulkActionPreviewItem.builder()
                        .gradebookId(gradebookId)
                        .eligible(false)
                        .message("Bảng điểm bị trùng trong danh sách yêu cầu.")
                        .build());
                continue;
            }
            try {
                GradebookSummaryDto summary = gradebookService.getGradebookSummary(gradebookId);
                String reason = eligibilityReason(request.getAction(), summary);
                results.add(previewItem(summary, reason == null, reason));
            } catch (NotFoundException ex) {
                results.add(GradebookBulkActionPreviewItem.builder()
                        .gradebookId(gradebookId)
                        .eligible(false)
                        .message("Không tìm thấy bảng điểm.")
                        .build());
            }
        }

        int eligibleCount = (int) results.stream().filter(GradebookBulkActionPreviewItem::isEligible).count();
        return GradebookBulkActionPreviewResponse.builder()
                .action(request.getAction())
                .selectedCount(request.getGradebookIds().size())
                .eligibleCount(eligibleCount)
                .ineligibleCount(results.size() - eligibleCount)
                .results(results)
                .build();
    }

    public GradebookBulkActionResponse execute(GradebookBulkActionRequest request, String actor) {
        LocalDateTime started = LocalDateTime.now();
        GradebookBulkOperation operation = GradebookBulkOperation.builder()
                .operationCode(UUID.randomUUID().toString())
                .actor(actor)
                .action(request.getAction().name())
                .selectedCount(request.getGradebookIds().size())
                .successCount(0)
                .failureCount(0)
                .skippedCount(0)
                .createdAt(started)
                .build();
        GradebookBulkOperation savedOperation = operationRepository.save(operation);
        if (savedOperation != null) {
            operation = savedOperation;
        }

        List<GradebookBulkActionItemResult> results = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();
        int skippedCount = 0;

        for (Long gradebookId : request.getGradebookIds()) {
            if (!uniqueIds.add(gradebookId)) {
                skippedCount++;
                GradebookBulkActionItemResult skipped = skipped(gradebookId, "Bảng điểm bị trùng trong danh sách yêu cầu.");
                results.add(skipped);
                saveHistoryItem(operation, request.getAction(), skipped);
                continue;
            }

            GradebookSummaryDto current;
            try {
                current = gradebookService.getGradebookSummary(gradebookId);
            } catch (NotFoundException ex) {
                GradebookBulkActionItemResult missing = failure(gradebookId, null, null, null, null,
                        "Không tìm thấy bảng điểm.");
                results.add(missing);
                saveHistoryItem(operation, request.getAction(), missing);
                continue;
            }

            String reason = eligibilityReason(request.getAction(), current);
            if (reason != null) {
                GradebookBulkActionItemResult failure = failure(gradebookId, current.getCourseCode(),
                        current.getCourseName(), current.getSectionCode(), current.getSemesterName(), reason,
                        current.getStatus());
                results.add(failure);
                saveHistoryItem(operation, request.getAction(), failure);
                continue;
            }

            try {
                GradebookSummaryDto updated = apply(request.getAction(), gradebookId, actor);
                GradebookBulkActionItemResult success = GradebookBulkActionItemResult.builder()
                        .gradebookId(gradebookId)
                        .courseCode(updated.getCourseCode())
                        .courseName(updated.getCourseName())
                        .sectionCode(updated.getSectionCode())
                        .semesterName(updated.getSemesterName())
                        .previousStatus(current.getStatus())
                        .status(updated.getStatus())
                        .successful(true)
                        .message(successMessage(request.getAction()))
                        .build();
                results.add(success);
                saveHistoryItem(operation, request.getAction(), success);
            } catch (BadRequestException | ConflictException | NotFoundException ex) {
                GradebookBulkActionItemResult failure = failure(gradebookId, current.getCourseCode(),
                        current.getCourseName(), current.getSectionCode(), current.getSemesterName(),
                        localizedFailure(ex.getMessage()), current.getStatus());
                results.add(failure);
                saveHistoryItem(operation, request.getAction(), failure);
            }
        }

        int successCount = (int) results.stream().filter(GradebookBulkActionItemResult::isSuccessful).count();
        int failureCount = results.size() - successCount - skippedCount;
        operation.setSuccessCount(successCount);
        operation.setFailureCount(failureCount);
        operation.setSkippedCount(skippedCount);
        operation.setCompletedAt(LocalDateTime.now());
        operationRepository.save(operation);
        return GradebookBulkActionResponse.builder()
                .operationCode(operation.getOperationCode())
                .selectedCount(request.getGradebookIds().size())
                .successCount(successCount)
                .failureCount(failureCount)
                .skippedCount(skippedCount)
                .results(results)
                .build();
    }

    @Transactional(readOnly = true)
    public GradebookBulkOperationPageDto searchHistory(
            String actor, String action, LocalDateTime fromTime, LocalDateTime toTime,
            String course, String section, String semester, String result, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Trang phải từ 0 trở lên và kích thước trang từ 1 đến 100.");
        }
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BadRequestException("Thời gian bắt đầu không được sau thời gian kết thúc.");
        }
        Page<GradebookBulkOperation> operations = operationRepository.search(
                blankToNull(actor), blankToNull(action), fromTime, toTime, blankToNull(course),
                blankToNull(section), blankToNull(semester), blankToNull(result),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<GradebookBulkOperationDto> content = operations.getContent().stream()
                .map(operation -> toOperationDto(operation, List.of()))
                .toList();
        return GradebookBulkOperationPageDto.builder()
                .content(content)
                .page(operations.getNumber())
                .size(operations.getSize())
                .totalElements(operations.getTotalElements())
                .totalPages(operations.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public GradebookBulkOperationDto getHistory(String operationCode) {
        GradebookBulkOperation operation = operationRepository.findByOperationCode(operationCode)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch sử thao tác bảng điểm."));
        List<GradebookBulkOperationItemDto> items = itemRepository
                .findByOperationIdOrderByIdAsc(operation.getId()).stream()
                .map(item -> GradebookBulkOperationItemDto.builder()
                        .gradebookId(item.getGradebookId())
                        .courseCode(item.getCourseCode())
                        .courseName(item.getCourseName())
                        .sectionCode(item.getSectionCode())
                        .semesterName(item.getSemesterName())
                        .previousStatus(item.getPreviousStatus())
                        .status(item.getStatus())
                        .action(item.getAction())
                        .resultStatus(item.getResultStatus())
                        .message(item.getMessage())
                        .build())
                .toList();
        return toOperationDto(operation, items);
    }

    private GradebookBulkActionPreviewItem previewItem(
            GradebookSummaryDto summary, boolean eligible, String message) {
        return GradebookBulkActionPreviewItem.builder()
                .gradebookId(summary.getGradebookId())
                .courseCode(summary.getCourseCode())
                .courseName(summary.getCourseName())
                .sectionCode(summary.getSectionCode())
                .semesterName(summary.getSemesterName())
                .status(summary.getStatus())
                .totalStudents(summary.getTotalStudents())
                .completedStudents(summary.getCompletedStudents())
                .incompleteStudents(summary.getIncompleteStudents())
                .studentsWithAnyGrade(summary.getStudentsWithAnyGrade())
                .studentsWithNoGrades(summary.getStudentsWithNoGrades())
                .studentsWithPartialGrades(summary.getStudentsWithPartialGrades())
                .eligible(eligible)
                .message(message == null ? "Đủ điều kiện thực hiện." : message)
                .build();
    }

    private String eligibilityReason(GradebookBulkAction action, GradebookSummaryDto summary) {
        return switch (action) {
            case OPEN -> "OPEN".equals(summary.getStatus()) ? "Bảng điểm đã mở." : null;
            case LOCK -> !"OPEN".equals(summary.getStatus()) ? "Chỉ bảng điểm đang mở mới có thể khóa." : null;
            case APPROVE -> {
                if (!"LOCKED".equals(summary.getStatus())) {
                    yield "Cần khóa bảng điểm trước khi duyệt.";
                }
                if (summary.getTotalStudents() == 0) {
                    yield "Không thể duyệt bảng điểm chưa có sinh viên hoặc điểm.";
                }
                if (summary.getCompletedStudents() == 0 && summary.getStudentsWithAnyGrade() == 0) {
                    yield "Không thể duyệt bảng điểm chưa có điểm.";
                }
                if (summary.getIncompleteStudents() > 0) {
                    yield "Không thể duyệt vì bảng điểm còn sinh viên chưa đủ điểm.";
                }
                yield null;
            }
            case PUBLISH -> {
                if (!"APPROVED".equals(summary.getStatus())) {
                    yield "Cần duyệt bảng điểm trước khi công bố.";
                }
                if (summary.getTotalStudents() == 0) {
                    yield "Không thể công bố bảng điểm chưa có sinh viên hoặc điểm.";
                }
                if (summary.getCompletedStudents() == 0 && summary.getStudentsWithAnyGrade() == 0) {
                    yield "Không thể công bố bảng điểm chưa có điểm.";
                }
                if (summary.getIncompleteStudents() > 0) {
                    yield "Không thể công bố vì bảng điểm còn sinh viên chưa đủ điểm.";
                }
                yield null;
            }
        };
    }

    private GradebookSummaryDto apply(GradebookBulkAction action, Long gradebookId, String actor) {
        return switch (action) {
            case OPEN -> gradebookService.unlock(gradebookId, actor);
            case LOCK -> gradebookService.lock(gradebookId, actor);
            case APPROVE -> gradebookService.approve(gradebookId, actor);
            case PUBLISH -> gradebookService.publish(gradebookId, actor);
        };
    }

    private String successMessage(GradebookBulkAction action) {
        return switch (action) {
            case OPEN -> "Đã mở bảng điểm.";
            case LOCK -> "Đã khóa bảng điểm.";
            case APPROVE -> "Đã duyệt bảng điểm.";
            case PUBLISH -> "Đã công bố bảng điểm.";
        };
    }

    private String localizedFailure(String message) {
        return switch (message) {
            case "Only an open gradebook can be locked" -> "Chỉ bảng điểm đang mở mới có thể khóa.";
            case "Gradebook is already open" -> "Bảng điểm đã ở trạng thái mở.";
            case "Gradebook must be locked before it can be approved" -> "Cần khóa bảng điểm trước khi duyệt.";
            case "Cannot approve a gradebook with incomplete grades" ->
                    "Không thể duyệt vì bảng điểm còn sinh viên chưa đủ điểm.";
            case "Gradebook must be approved before it can be published" ->
                    "Cần duyệt bảng điểm trước khi công bố.";
            case "Cannot publish a gradebook with incomplete grades" ->
                    "Không thể công bố vì bảng điểm còn sinh viên chưa đủ điểm.";
            case "Gradebook not found" -> "Không tìm thấy bảng điểm.";
            default -> message;
        };
    }

    private GradebookBulkActionItemResult skipped(Long gradebookId, String message) {
        return GradebookBulkActionItemResult.builder()
                .gradebookId(gradebookId)
                .successful(false)
                .skipped(true)
                .message(message)
                .build();
    }

    private GradebookBulkActionItemResult failure(
            Long gradebookId, String courseCode, String courseName, String sectionCode,
            String semesterName, String message) {
        return failure(gradebookId, courseCode, courseName, sectionCode, semesterName, message, null);
    }

    private GradebookBulkActionItemResult failure(
            Long gradebookId, String courseCode, String courseName, String sectionCode,
            String semesterName, String message, String status) {
        return GradebookBulkActionItemResult.builder()
                .gradebookId(gradebookId)
                .courseCode(courseCode)
                .courseName(courseName)
                .sectionCode(sectionCode)
                .semesterName(semesterName)
                .previousStatus(status)
                .status(status)
                .successful(false)
                .skipped(false)
                .message(message)
                .build();
    }

    private void saveHistoryItem(
            GradebookBulkOperation operation, GradebookBulkAction action, GradebookBulkActionItemResult result) {
        String resultStatus = result.isSuccessful() ? "SUCCESS" : result.isSkipped() ? "SKIPPED" : "FAILURE";
        itemRepository.save(GradebookBulkOperationItem.builder()
                .operationId(operation.getId())
                .gradebookId(result.getGradebookId())
                .courseCode(result.getCourseCode())
                .courseName(result.getCourseName())
                .sectionCode(result.getSectionCode())
                .semesterName(result.getSemesterName())
                .previousStatus(result.getPreviousStatus())
                .status(result.getStatus())
                .action(action.name())
                .resultStatus(resultStatus)
                .message(result.getMessage())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private GradebookBulkOperationDto toOperationDto(
            GradebookBulkOperation operation, List<GradebookBulkOperationItemDto> items) {
        return GradebookBulkOperationDto.builder()
                .id(operation.getId())
                .operationCode(operation.getOperationCode())
                .actor(operation.getActor())
                .action(operation.getAction())
                .selectedCount(operation.getSelectedCount())
                .successCount(operation.getSuccessCount())
                .failureCount(operation.getFailureCount())
                .skippedCount(operation.getSkippedCount())
                .createdAt(operation.getCreatedAt())
                .completedAt(operation.getCompletedAt())
                .items(items)
                .build();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
