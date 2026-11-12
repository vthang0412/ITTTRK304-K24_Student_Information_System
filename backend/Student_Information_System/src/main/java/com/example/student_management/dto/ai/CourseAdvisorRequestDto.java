package com.example.student_management.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorRequestDto {
    private String careerOrientation; // ví dụ: "Trí tuệ nhân tạo", "Phát triển Web", "An toàn thông tin", "Kỹ thuật phần mềm"
    private Integer targetCredits;    // Số tín chỉ mong muốn đăng ký (mặc định: 15 - 18)
    private String customNote;        // Ghi chú hoặc câu hỏi định hướng của sinh viên
}

