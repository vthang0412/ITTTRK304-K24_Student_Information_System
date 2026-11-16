package com.example.student_management.dto.exam;

import com.example.student_management.entity.exam.ExamRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamRoomDto {
    private Long id;
    private String roomCode;
    private String roomName;
    private String building;
    private Integer capacity;
    private String description;
    private ExamRoomStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

