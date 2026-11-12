package com.example.student_management.dto.grade;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GradebookBulkActionRequest {
    @NotEmpty(message = "Vui lòng chọn ít nhất một bảng điểm")
    @Size(max = 200, message = "Mỗi lần chỉ được xử lý tối đa 200 bảng điểm")
    private List<@NotNull Long> gradebookIds;

    @NotNull(message = "Hành động bảng điểm là bắt buộc")
    private GradebookBulkAction action;
}
