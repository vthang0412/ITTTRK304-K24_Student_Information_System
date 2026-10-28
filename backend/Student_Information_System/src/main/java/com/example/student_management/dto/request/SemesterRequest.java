package com.example.student_management.dto.request;



import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SemesterRequest {
    @NotBlank(message = "Semester code is required")
    @Size(max = 20)
    private String semesterCode;

    @NotBlank(message = "Semester name is required")
    @Size(max = 100)
    private String semesterName;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private LocalDateTime registrationStart;

    private LocalDateTime registrationEnd;

    private String status;
}
