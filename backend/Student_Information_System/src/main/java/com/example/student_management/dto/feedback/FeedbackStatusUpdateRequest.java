package com.example.student_management.dto.feedback;

import com.example.student_management.entity.feedback.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackStatusUpdateRequest {
    @NotNull(message = "Trạng thái là bắt buộc")
    private FeedbackStatus status;

    private String note;
}

