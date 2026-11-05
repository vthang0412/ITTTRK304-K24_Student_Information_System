package com.example.student_management.dto.feedback;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackAssignRequest {
    @NotNull(message = "ID giảng viên là bắt buộc")
    private Long lecturerId;

    private String adminNote;
}

