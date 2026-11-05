package com.example.student_management.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackMessageRequest {
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String message;
}

