package com.example.student_management.dto.academic;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseSectionRequest {
    @NotBlank(message = "Section code is required")
    @Size(max = 30)
    private String sectionCode;

    @NotNull(message = "Course id is required")
    private Long courseId;

    @NotNull(message = "Semester id is required")
    private Long semesterId;

    private Long lecturerId;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be >= 1")
    private Integer capacity;

    private String schedule;

    private String status;
}
