package com.example.student_management.controller.academic;

import com.example.student_management.dto.academic.RecurringScheduleRequest;
import com.example.student_management.dto.academic.RecurringScheduleSessionResponse;
import com.example.student_management.service.academic.RecurringCourseScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course-sections/{sectionId}/recurring-schedules")
@PreAuthorize("hasRole('ADMIN')")
public class RecurringCourseScheduleController {
    private final RecurringCourseScheduleService scheduleService;

    public RecurringCourseScheduleController(RecurringCourseScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<List<RecurringScheduleSessionResponse>> get(@PathVariable Long sectionId) {
        return ResponseEntity.ok(scheduleService.getActiveSessions(sectionId));
    }

    @PostMapping("/check")
    public ResponseEntity<Void> check(@PathVariable Long sectionId,
                                      @Valid @RequestBody RecurringScheduleRequest request) {
        scheduleService.check(sectionId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    public ResponseEntity<List<RecurringScheduleSessionResponse>> replace(
            @PathVariable Long sectionId,
            @Valid @RequestBody RecurringScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.replace(sectionId, request));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancel(@PathVariable Long sectionId) {
        scheduleService.cancelActiveSessions(sectionId);
        return ResponseEntity.noContent().build();
    }
}
