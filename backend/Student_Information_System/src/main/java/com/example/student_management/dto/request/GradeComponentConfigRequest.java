package com.example.student_management.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GradeComponentConfigRequest {
    @NotNull(message = "Components list is required")
    @Valid
    private List<GradeComponentRequest> components = new ArrayList<>();
}
