package com.example.student_management.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAdvisorRequestDto {
    @Size(max = 255)
    private String careerOrientation;

    @Min(1)
    @Max(30)
    private Integer targetCredits;

    @Size(max = 100)
    @Pattern(
            regexp = "^\\s*$|^(?:từ\\s+|tu\\s+)?\\d{1,2}\\s*(?:(?:-|–|—|\\.\\.|đến|den|to|till)\\s*\\d{1,2})?$",
            message = "Số tín chỉ mong muốn phải là một số hoặc khoảng hợp lệ (ví dụ: 12, 12-15, từ 12 đến 15)"
    )
    private String targetCreditsRange;

    @Size(max = 2000)
    private String customNote;
}

