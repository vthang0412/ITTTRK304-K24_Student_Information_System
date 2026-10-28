package com.example.student_management.dto.request;



import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CourseRequest {
    @NotBlank(message = "Course code is required")
    @Size(max = 20)
    private String courseCode;

    @NotBlank(message = "Course name is required")
    @Size(max = 150)
    private String courseName;

    @NotNull(message = "Credits is required")
    @Min(value = 1, message = "Credits must be >= 1")
    @Max(value = 6, message = "Credits must be <= 6")
    private Integer credits;

    @Size(max = 1000)
    private String description;

    private String status;
}
