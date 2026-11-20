package com.example.student_management.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIMessageFeedbackRequest {
    @NotBlank(message = "Đánh giá không được để trống")
    @Size(max = 20, message = "Đánh giá không được vượt quá 20 ký tự")
    private String feedback;
}
