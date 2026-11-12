package com.example.student_management.service;

import com.example.student_management.dto.grade.GradebookBulkAction;
import com.example.student_management.dto.grade.GradebookBulkActionRequest;
import com.example.student_management.dto.grade.GradebookBulkActionResponse;
import com.example.student_management.dto.grade.GradebookBulkActionPreviewResponse;
import com.example.student_management.dto.grade.GradebookSummaryDto;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.grade.GradebookBulkOperationItemRepository;
import com.example.student_management.repository.grade.GradebookBulkOperationRepository;
import com.example.student_management.service.grade.GradebookBulkActionService;
import com.example.student_management.service.grade.GradebookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradebookBulkActionServiceTest {
    @Mock
    private GradebookService gradebookService;
    @Mock
    private GradebookBulkOperationRepository operationRepository;
    @Mock
    private GradebookBulkOperationItemRepository itemRepository;

    @InjectMocks
    private GradebookBulkActionService bulkActionService;

    @Test
    void executeProcessesEachGradebookIndependentlyAndReturnsPartialFailures() {
        when(gradebookService.getGradebookSummary(11L)).thenReturn(summary(11L, "SEC-01", "OPEN"));
        when(gradebookService.getGradebookSummary(12L)).thenReturn(summary(12L, "SEC-02", "LOCKED"));
        when(gradebookService.lock(11L, "admin")).thenReturn(summary(11L, "SEC-01", "LOCKED"));

        GradebookBulkActionResponse response = bulkActionService.execute(
                request(GradebookBulkAction.LOCK, 11L, 12L), "admin");

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertTrue(response.getResults().get(0).isSuccessful());
        assertEquals("SEC-01", response.getResults().get(0).getSectionCode());
        assertFalse(response.getResults().get(1).isSuccessful());
        assertEquals("SEC-02", response.getResults().get(1).getSectionCode());
        assertTrue(response.getResults().get(1).getMessage().contains("đang mở"));
        verify(gradebookService).lock(11L, "admin");
        verify(gradebookService, never()).lock(12L, "admin");
    }

    @Test
    void executeUsesExistingUnlockFlowForOpenAction() {
        when(gradebookService.getGradebookSummary(21L)).thenReturn(summary(21L, "SEC-21", "LOCKED"));
        when(gradebookService.unlock(21L, "admin")).thenReturn(summary(21L, "SEC-21", "OPEN"));

        GradebookBulkActionResponse response = bulkActionService.execute(
                request(GradebookBulkAction.OPEN, 21L), "admin");

        assertEquals(1, response.getSuccessCount());
        assertEquals("OPEN", response.getResults().get(0).getStatus());
        verify(gradebookService).unlock(21L, "admin");
        verify(gradebookService, never()).lock(anyLong(), anyString());
    }

    @Test
    void executeReportsUnknownGradebookWithoutAbortingRemainingItems() {
        when(gradebookService.getGradebookSummary(31L)).thenThrow(new NotFoundException("Gradebook not found"));
        when(gradebookService.getGradebookSummary(32L)).thenReturn(summary(32L, "SEC-32", "LOCKED"));
        when(gradebookService.approve(32L, "admin")).thenReturn(summary(32L, "SEC-32", "APPROVED"));

        GradebookBulkActionResponse response = bulkActionService.execute(
                request(GradebookBulkAction.APPROVE, 31L, 32L), "admin");

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals(31L, response.getResults().get(0).getGradebookId());
        assertFalse(response.getResults().get(0).isSuccessful());
        assertTrue(response.getResults().get(0).getMessage().contains("Không tìm thấy"));
        assertEquals("APPROVED", response.getResults().get(1).getStatus());
    }

    @Test
    void executeSkipsDuplicateIdsAndExecutesOnlyOnce() {
        when(gradebookService.getGradebookSummary(41L)).thenReturn(summary(41L, "SEC-41", "APPROVED"));
        when(gradebookService.publish(41L, "admin")).thenReturn(summary(41L, "SEC-41", "PUBLISHED"));

        GradebookBulkActionResponse response = bulkActionService.execute(
                request(GradebookBulkAction.PUBLISH, 41L, 41L), "admin");

        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(1, response.getSkippedCount());
        assertTrue(response.getResults().get(0).isSuccessful());
        assertFalse(response.getResults().get(1).isSuccessful());
        assertTrue(response.getResults().get(1).isSkipped());
        verify(gradebookService, times(1)).publish(41L, "admin");
    }

    @Test
    void previewReportsEligibilityAndDoesNotMutateGradebooks() {
        when(gradebookService.getGradebookSummary(51L)).thenReturn(summary(51L, "SEC-51", "LOCKED"));
        when(gradebookService.getGradebookSummary(52L)).thenReturn(GradebookSummaryDto.builder()
                .gradebookId(52L).sectionCode("SEC-52").status("LOCKED")
                .totalStudents(2).completedStudents(1).incompleteStudents(1)
                .studentsWithAnyGrade(2).studentsWithPartialGrades(1).studentsWithNoGrades(0).build());

        GradebookBulkActionPreviewResponse response = bulkActionService.preview(
                request(GradebookBulkAction.APPROVE, 51L, 52L));

        assertEquals(1, response.getEligibleCount());
        assertEquals(1, response.getIneligibleCount());
        assertTrue(response.getResults().get(0).isEligible());
        assertFalse(response.getResults().get(1).isEligible());
        verify(gradebookService, never()).approve(anyLong(), anyString());
    }

    @Test
    void executePersistsBatchAndPerGradebookHistory() {
        when(gradebookService.getGradebookSummary(61L)).thenReturn(summary(61L, "SEC-61", "OPEN"));
        when(gradebookService.lock(61L, "admin")).thenReturn(summary(61L, "SEC-61", "LOCKED"));

        GradebookBulkActionResponse response = bulkActionService.execute(
                request(GradebookBulkAction.LOCK, 61L), "admin");

        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(0, response.getSkippedCount());
        verify(operationRepository, times(2)).save(any());
        verify(itemRepository).save(any());
    }

    private GradebookSummaryDto summary(Long id, String sectionCode, String status) {
        return GradebookSummaryDto.builder()
                .gradebookId(id)
                .sectionId(id + 100)
                .courseCode("CS" + id)
                .courseName("Course " + id)
                .sectionCode(sectionCode)
                .semesterName("Semester")
                .status(status)
                .totalStudents(1)
                .completedStudents(1)
                .incompleteStudents(0)
                .studentsWithAnyGrade(1)
                .studentsWithNoGrades(0)
                .studentsWithPartialGrades(0)
                .build();
    }

    private GradebookBulkActionRequest request(GradebookBulkAction action, Long... ids) {
        GradebookBulkActionRequest request = new GradebookBulkActionRequest();
        request.setAction(action);
        request.setGradebookIds(List.of(ids));
        return request;
    }
}
