package com.example.student_management.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequest {
    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    @Size(max = 2000, message = "Câu hỏi tối đa 2000 ký tự")
    private String message;

    @Builder.Default
    private Boolean continueResponse = false;

    private Long continueFromMessageId;

    private Integer continuationCount;

    public AIChatRequest(String message) {
        this.message = message;
    }
}

