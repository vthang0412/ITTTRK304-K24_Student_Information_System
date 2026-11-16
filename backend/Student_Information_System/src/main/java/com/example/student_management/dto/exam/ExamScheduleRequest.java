package com.example.student_management.dto.exam;

import com.example.student_management.entity.exam.ExamScheduleStatus;
import com.example.student_management.entity.exam.ExamType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamScheduleRequest {

    @NotNull(message = "Lớp học phần không được để trống")
    private Long courseSectionId;

    @NotNull(message = "Ngày thi không được để trống")
    private LocalDate examDate;

    @NotNull(message = "Giờ bắt đầu thi không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc thi không được để trống")
    private LocalTime endTime;

    @NotNull(message = "Hình thức thi không được để trống (OFFLINE / ONLINE)")
    private ExamType examType;

    // Bắt buộc nếu examType == OFFLINE
    private Long roomId;

    // Tùy chọn hoặc tên nền tảng (Google Meet, MS Teams, giảng đường...)
    private String examLocation;

    // Link phòng thi (nếu thi ONLINE)
    private String examLink;

    private ExamScheduleStatus status; // Mặc định SCHEDULED nếu để trống

    private String notes;
}

