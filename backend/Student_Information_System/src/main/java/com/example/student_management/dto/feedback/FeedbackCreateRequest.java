package com.example.student_management.dto.feedback;

import com.example.student_management.entity.feedback.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackCreateRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotNull(message = "Loại phản ánh không được để trống")
    private FeedbackType feedbackType;

    @NotBlank(message = "Nội dung phản ánh không được để trống")
    private String content;

    private Long courseSectionId; // Tùy chọn nếu phản ánh liên quan tới lớp học phần/điểm số
}

