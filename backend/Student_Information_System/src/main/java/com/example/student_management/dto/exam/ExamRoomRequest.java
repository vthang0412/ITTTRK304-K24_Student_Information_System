package com.example.student_management.dto.exam;

import com.example.student_management.entity.exam.ExamRoomStatus;
import jakarta.validation.constraints.Min;
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
public class ExamRoomRequest {

    @NotBlank(message = "Mã phòng thi không được để trống")
    private String roomCode;

    @NotBlank(message = "Tên phòng thi không được để trống")
    private String roomName;

    private String building;

    @NotNull(message = "Sức chứa phòng thi không được để trống")
    @Min(value = 1, message = "Sức chứa phòng thi phải lớn hơn 0")
    private Integer capacity;

    private String description;

    private ExamRoomStatus status;
}

