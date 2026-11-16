package com.example.student_management.service.academic;

import com.example.student_management.dto.academic.BulkEnrollmentResponse;
import com.example.student_management.dto.academic.BulkEnrollmentPreviewResponse;
import com.example.student_management.dto.academic.BulkEnrollmentHistoryDto;
import com.example.student_management.dto.academic.BulkEnrollmentHistoryDetailDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface BulkEnrollmentService {
    BulkEnrollmentResponse approve(String adminUsername, List<Long> enrollmentIds);

    BulkEnrollmentResponse reject(String adminUsername, List<Long> enrollmentIds, String reason);

    BulkEnrollmentResponse reject(
            String adminUsername,
            List<Long> enrollmentIds,
            String reason,
            Map<Long, String> reasonsByEnrollmentId
    );

    BulkEnrollmentPreviewResponse preview(String adminUsername, List<Long> enrollmentIds, String action);

    Page<BulkEnrollmentHistoryDto> findHistory(
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
    );

    BulkEnrollmentHistoryDetailDto getHistory(Long operationId);
}
