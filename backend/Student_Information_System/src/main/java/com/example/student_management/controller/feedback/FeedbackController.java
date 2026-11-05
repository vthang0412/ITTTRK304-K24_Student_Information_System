package com.example.student_management.controller.feedback;

import com.example.student_management.dto.feedback.*;
import com.example.student_management.entity.feedback.FeedbackStatus;
import com.example.student_management.entity.feedback.FeedbackType;
import com.example.student_management.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {
    @Autowired
    private FeedbackService feedbackService;

    // 1. Sinh viên gửi phản ánh / ticket mới
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<FeedbackDto> create(
            @Valid @RequestBody FeedbackCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.createFeedback(authentication.getName(), request));
    }

    // 2. Sinh viên theo dõi danh sách phản ánh của mình
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<FeedbackDto>> getMyFeedbacks(Authentication authentication) {
        return ResponseEntity.ok(feedbackService.getMyFeedbacks(authentication.getName()));
    }

    // 3. Giảng viên xem các phản ánh được phân công cho mình
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<List<FeedbackDto>> getAssignedFeedbacks(Authentication authentication) {
        return ResponseEntity.ok(feedbackService.getAssignedFeedbacks(authentication.getName()));
    }

    // 4. Admin xem toàn bộ danh sách phản ánh (có lọc theo trạng thái và phân loại)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeedbackDto>> getAll(
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackType type) {
        return ResponseEntity.ok(feedbackService.getAllFeedbacks(status, type));
    }

    // 5. Xem chi tiết phản ánh kèm toàn bộ lịch sử trao đổi
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'ADMIN')")
    public ResponseEntity<FeedbackDto> getById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.getFeedbackById(authentication.getName(), id));
    }

    // 6. Admin phân công giảng viên phụ trách xử lý
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeedbackDto> assign(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackAssignRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(feedbackService.assignToLecturer(username, id, request));
    }

    // 7. Cập nhật trạng thái phản ánh (Admin hoặc Giảng viên phụ trách)
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<FeedbackDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackStatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.updateStatus(authentication.getName(), id, request));
    }

    // 8. Thêm tin nhắn trao đổi / phản hồi vào ticket (Sinh viên, Giảng viên,
    // Admin)
    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'ADMIN')")
    public ResponseEntity<FeedbackMessageDto> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(feedbackService.addMessage(authentication.getName(), id, request));
    }
}
