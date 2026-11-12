package com.example.student_management.dto.academic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class RecurringScheduleSessionRequest {
    @NotNull
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @NotNull
    @Min(1)
    private Integer startPeriod;

    @NotNull
    @Min(1)
    private Integer endPeriod;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotBlank
    @Size(max = 100)
    private String classroom;
}
