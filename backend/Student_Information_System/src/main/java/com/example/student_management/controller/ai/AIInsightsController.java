package com.example.student_management.controller.ai;

import com.example.student_management.dto.ai.AIInsightsDto;
import com.example.student_management.service.ai.AIInsightsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/student/ai/insights")
@PreAuthorize("hasRole('STUDENT')")
public class AIInsightsController {

    @Autowired
    private AIInsightsService aiInsightsService;

    /**
     * 1. Lấy phân tích kết quả học tập, GPA, tín chỉ, xu hướng theo học kỳ và đề xuất cải thiện
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getInsights(Authentication authentication) {
        AIInsightsDto insights = aiInsightsService.getInsights(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", insights);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Yêu cầu phân tích lại dữ liệu học tập (bỏ qua cache, gọi AI tổng hợp lại)
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshInsights(Authentication authentication) {
        AIInsightsDto insights = aiInsightsService.refreshInsights(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", insights);
        return ResponseEntity.ok(response);
    }
}

