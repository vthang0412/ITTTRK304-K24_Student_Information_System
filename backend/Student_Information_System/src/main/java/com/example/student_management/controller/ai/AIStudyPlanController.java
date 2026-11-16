package com.example.student_management.controller.ai;

import com.example.student_management.dto.ai.*;
import com.example.student_management.service.ai.AIStudyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/ai/study-plans")
@PreAuthorize("hasRole('STUDENT')")
public class AIStudyPlanController {

    @Autowired
    private AIStudyPlanService aiStudyPlanService;

    /**
     * 1. Tạo kế hoạch học tập cá nhân theo tuần (AI tự động đề xuất lịch học theo ngày)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createStudyPlan(
            Authentication authentication,
            @RequestBody(required = false) CreateStudyPlanRequestDto request) {
        StudyPlanDto plan = aiStudyPlanService.createStudyPlan(
                authentication.getName(),
                request != null ? request : new CreateStudyPlanRequestDto()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Lấy danh sách kế hoạch học tập của sinh viên
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStudentStudyPlans(Authentication authentication) {
        List<StudyPlanDto> plans = aiStudyPlanService.getStudentStudyPlans(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", plans);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Xem chi tiết kế hoạch học tập kèm danh sách nhiệm vụ và % hoàn thành
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getStudyPlanDetail(
            Authentication authentication,
            @PathVariable Long id) {
        StudyPlanDto plan = aiStudyPlanService.getStudyPlanDetail(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", plan);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Chỉnh sửa thông tin kế hoạch học tập (tiêu đề, mục tiêu, trạng thái)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateStudyPlan(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody UpdateStudyPlanRequestDto request) {
        StudyPlanDto updated = aiStudyPlanService.updateStudyPlan(authentication.getName(), id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Xóa kế hoạch học tập
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteStudyPlan(
            Authentication authentication,
            @PathVariable Long id) {
        aiStudyPlanService.deleteStudyPlan(authentication.getName(), id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Xóa kế hoạch học tập thành công");
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Cập nhật trạng thái nhiệm vụ (PENDING / COMPLETED / SKIPPED) hoặc chỉnh sửa nhiệm vụ
     */
    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<Map<String, Object>> updatePlanItem(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestBody UpdateStudyPlanItemRequestDto request) {
        StudyPlanItemDto updatedItem = aiStudyPlanService.updatePlanItem(authentication.getName(), id, itemId, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", updatedItem);
        return ResponseEntity.ok(response);
    }
}

