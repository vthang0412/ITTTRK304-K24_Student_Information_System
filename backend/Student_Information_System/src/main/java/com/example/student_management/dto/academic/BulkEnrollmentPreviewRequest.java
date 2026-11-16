package com.example.student_management.dto.academic;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkEnrollmentPreviewRequest {
    @NotEmpty
    @Size(max = 1000)
    private List<@NotNull Long> enrollmentIds;

    @NotNull
    @Pattern(regexp = "APPROVE|REJECT")
    private String action;
}
