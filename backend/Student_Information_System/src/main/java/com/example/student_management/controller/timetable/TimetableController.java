package com.example.student_management.controller.timetable;

import com.example.student_management.dto.timetable.TimetableWeekResponse;
import com.example.student_management.service.timetable.TimetableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {
    @Autowired
    private TimetableService timetableService;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TimetableWeekResponse> getWeek(
            Authentication authentication,
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(timetableService.getWeek(
                authentication.getName(), semesterId, weekStart, page, size));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TimetableWeekResponse> getAdminWeek(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "lecturerId", required = false) Long lecturerId,
            @RequestParam(value = "classroom", required = false) String classroom,
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(timetableService.getAdminWeek(
                semesterId, sectionId, lecturerId, classroom, weekStart, page, size));
    }
}
