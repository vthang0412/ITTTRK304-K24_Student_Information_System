package com.example.student_management.controller.ai;

import com.example.student_management.dto.ai.CourseAdvisorRequestDto;
import com.example.student_management.dto.ai.CourseAdvisorResponseDto;
import com.example.student_management.service.ai.AICourseAdvisorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/student/ai/course-advisor")
@PreAuthorize("hasRole('STUDENT')")
public class AICourseAdvisorController {

    @Autowired
    private AICourseAdvisorService aiCourseAdvisorService;

    /**
     * 1. Lấy danh sách môn học tư vấn/đề xuất cho học kỳ tiếp theo
     * Phân tích môn tiên quyết, lớp học phần mở, tình trạng chỗ trống và cảnh báo xung đột lịch.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCourseRecommendations(Authentication authentication) {
        CourseAdvisorResponseDto recommendations = aiCourseAdvisorService.getCourseRecommendations(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", recommendations);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Tư vấn môn học cá nhân hóa theo định hướng nghề nghiệp, mục tiêu tín chỉ và ghi chú
     */
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommendCoursesWithPreferences(
            Authentication authentication,
            @RequestBody(required = false) CourseAdvisorRequestDto request) {
        CourseAdvisorResponseDto recommendations = aiCourseAdvisorService.recommendCoursesWithPreferences(
                authentication.getName(),
                request != null ? request : new CourseAdvisorRequestDto()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", recommendations);
        return ResponseEntity.ok(response);
    }
}

